package io.aeron.rpc.reactor;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event loop for handling Aeron messages in a Reactor pattern.
 */
public class EventLoop implements Agent {
    private static final Logger logger = LoggerFactory.getLogger(EventLoop.class);
    private static final int FRAGMENT_LIMIT = 10;
    
    private final String name;
    private final Aeron aeron;
    private final IdleStrategy idleStrategy;
    private final List<Subscription> subscriptions;
    private final List<EventHandler> eventHandlers;
    private final AtomicBoolean running;
    private final FragmentHandler fragmentAssembler;
    private AgentRunner agentRunner;

    public EventLoop(String name, Aeron aeron) {
        this.name = name;
        this.aeron = aeron;
        this.idleStrategy = new SleepingIdleStrategy(100);
        this.subscriptions = new ArrayList<>();
        this.eventHandlers = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.fragmentAssembler = new FragmentAssembler(this::onFragment);
    }

    public void addSubscription(Subscription subscription) {
        subscriptions.add(subscription);
    }

    public void addHandler(EventHandler handler) {
        eventHandlers.add(handler);
    }

    public void removeHandler(EventHandler handler) {
        eventHandlers.remove(handler);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            agentRunner = new AgentRunner(idleStrategy, Throwable::printStackTrace, null, this);
            AgentRunner.startOnThread(agentRunner);
            logger.info("Event loop {} started", name);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            AgentRunner.closeAll();
            logger.info("Event loop {} stopped", name);
        }
    }

    @Override
    public int doWork() {
        if (!running.get()) {
            return 0;
        }

        int totalWork = 0;
        for (Subscription subscription : subscriptions) {
            final int fragmentsRead = subscription.poll(fragmentAssembler, FRAGMENT_LIMIT);
            if (0 == fragmentsRead) {
                idleStrategy.idle(0);
            }
            totalWork += fragmentsRead;
        }
        return totalWork;
    }

    private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        try {
            for (EventHandler handler : eventHandlers) {
                if (handler.canHandle(header)) {
                    handler.onEvent(buffer, offset, length, header);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing fragment", e);
        }
    }

    @Override
    public String roleName() {
        return name;
    }

    public boolean isRunning() {
        return running.get();
    }

    public interface EventHandler {
        boolean canHandle(Header header);
        void onEvent(DirectBuffer buffer, int offset, int length, Header header);
    }
}

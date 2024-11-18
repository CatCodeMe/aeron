package io.aeron.rpc.example;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.rpc.JsonSerializer;
import io.aeron.rpc.RpcClient;
import io.aeron.rpc.RpcServer;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Example demonstrating the RPC framework with a calculator service.
 */
public class CalculatorExample {
    private static final String CHANNEL = "aeron:ipc";
    private static final int REQUEST_STREAM_ID = 1;
    private static final int RESPONSE_STREAM_ID = 2;

    public static void main(String[] args) {
        // Start a media driver
        final MediaDriver.Context ctx = new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(true);
        
        final MediaDriver driver = MediaDriver.launch(ctx);

        // Create Aeron instance
        final Aeron aeron = Aeron.connect(new Aeron.Context()
            .aeronDirectoryName(driver.aeronDirectoryName()));

        final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
        final JsonSerializer serializer = new JsonSerializer();

        // Create and start the server
        final RpcServer server = new RpcServer(
            aeron,
            CHANNEL,
            REQUEST_STREAM_ID,
            CHANNEL,
            RESPONSE_STREAM_ID,
            idleStrategy);

        // Register the calculator service
        server.register(new CalculatorService(serializer));

        // Start the server in a separate thread
        Thread serverThread = new Thread(server::start);
        serverThread.start();

        // Create a client
        final RpcClient client = new RpcClient(
            aeron,
            CHANNEL,
            REQUEST_STREAM_ID,
            CHANNEL,
            RESPONSE_STREAM_ID,
            idleStrategy);

        try {
            // Prepare request payload
            final UnsafeBuffer requestBuffer = new UnsafeBuffer(new byte[1024]);
            final CalculatorService.Numbers numbers = new CalculatorService.Numbers(10.0, 5.0);
            final int length = serializer.serialize(numbers, requestBuffer, 0);

            // Make synchronous calls
            System.out.println("Making synchronous calls...");

            // Addition
            double result = serializer.deserialize(
                client.call("calculator", "add", new UnsafeBuffer(requestBuffer, 0, length)),
                0,
                8,
                Double.class);
            System.out.println("10 + 5 = " + result);

            // Subtraction
            result = serializer.deserialize(
                client.call("calculator", "subtract", new UnsafeBuffer(requestBuffer, 0, length)),
                0,
                8,
                Double.class);
            System.out.println("10 - 5 = " + result);

            // Multiplication
            result = serializer.deserialize(
                client.call("calculator", "multiply", new UnsafeBuffer(requestBuffer, 0, length)),
                0,
                8,
                Double.class);
            System.out.println("10 * 5 = " + result);

            // Division
            result = serializer.deserialize(
                client.call("calculator", "divide", new UnsafeBuffer(requestBuffer, 0, length)),
                0,
                8,
                Double.class);
            System.out.println("10 / 5 = " + result);

            // Make an asynchronous call
            System.out.println("\nMaking asynchronous call...");
            client.callAsync("calculator", "add", new UnsafeBuffer(requestBuffer, 0, length))
                .thenApply(buffer -> serializer.deserialize(buffer, 0, 8, Double.class))
                .thenAccept(r -> System.out.println("Async result of 10 + 5 = " + r))
                .get(); // Wait for async result

            // Try division by zero
            System.out.println("\nTrying division by zero...");
            try {
                final CalculatorService.Numbers zeroDivision = new CalculatorService.Numbers(10.0, 0.0);
                final int zeroLength = serializer.serialize(zeroDivision, requestBuffer, 0);
                client.call("calculator", "divide", new UnsafeBuffer(requestBuffer, 0, zeroLength));
            } catch (Exception e) {
                System.out.println("Caught expected error: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("\nShutting down...");
            CloseHelper.quietClose(client);
            CloseHelper.quietClose(server);
            CloseHelper.quietClose(aeron);
            CloseHelper.quietClose(driver);
        }
    }
}

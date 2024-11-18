package io.aeron.rpc;

/**
 * Interface for receiving streaming RPC responses.
 *
 * @param <T> type of stream items
 */
public interface RpcStreamSubscriber<T> {
    /**
     * Called when a new item is received.
     *
     * @param value the received item
     */
    void onNext(T value);

    /**
     * Called when an error occurs.
     *
     * @param t the error
     */
    void onError(Throwable t);

    /**
     * Called when the stream completes.
     */
    void onComplete();

    /**
     * Called when the stream is ready to receive items.
     * Optional - default implementation does nothing.
     */
    default void onSubscribe() {
        // Optional subscription handling
    }

    /**
     * Request more items from the stream.
     * Optional - default implementation does nothing.
     *
     * @param n number of items to request
     */
    default void request(long n) {
        // Optional backpressure handling
    }

    /**
     * Cancel the stream subscription.
     * Optional - default implementation does nothing.
     */
    default void cancel() {
        // Optional cancellation handling
    }
}

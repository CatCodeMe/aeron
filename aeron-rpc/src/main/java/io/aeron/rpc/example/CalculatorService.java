package io.aeron.rpc.example;

import io.aeron.rpc.RpcService;
import io.aeron.rpc.Serializer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Example calculator service implementation.
 */
public class CalculatorService implements RpcService {
    private final Serializer serializer;
    private final MutableDirectBuffer responseBuffer;

    public CalculatorService(Serializer serializer) {
        this.serializer = serializer;
        this.responseBuffer = new UnsafeBuffer(new byte[1024]);
    }

    @Override
    public String serviceName() {
        return "calculator";
    }

    @Override
    public DirectBuffer handleRequest(String methodName, DirectBuffer payload) throws Exception {
        switch (methodName) {
            case "add":
                return handleAdd(payload);
            case "subtract":
                return handleSubtract(payload);
            case "multiply":
                return handleMultiply(payload);
            case "divide":
                return handleDivide(payload);
            default:
                throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    private DirectBuffer handleAdd(DirectBuffer payload) {
        Numbers numbers = serializer.deserialize(payload, 0, payload.capacity(), Numbers.class);
        double result = numbers.a + numbers.b;
        int length = serializer.serialize(result, responseBuffer, 0);
        return new UnsafeBuffer(responseBuffer, 0, length);
    }

    private DirectBuffer handleSubtract(DirectBuffer payload) {
        Numbers numbers = serializer.deserialize(payload, 0, payload.capacity(), Numbers.class);
        double result = numbers.a - numbers.b;
        int length = serializer.serialize(result, responseBuffer, 0);
        return new UnsafeBuffer(responseBuffer, 0, length);
    }

    private DirectBuffer handleMultiply(DirectBuffer payload) {
        Numbers numbers = serializer.deserialize(payload, 0, payload.capacity(), Numbers.class);
        double result = numbers.a * numbers.b;
        int length = serializer.serialize(result, responseBuffer, 0);
        return new UnsafeBuffer(responseBuffer, 0, length);
    }

    private DirectBuffer handleDivide(DirectBuffer payload) {
        Numbers numbers = serializer.deserialize(payload, 0, payload.capacity(), Numbers.class);
        if (numbers.b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        double result = numbers.a / numbers.b;
        int length = serializer.serialize(result, responseBuffer, 0);
        return new UnsafeBuffer(responseBuffer, 0, length);
    }

    public static class Numbers {
        public double a;
        public double b;

        // Default constructor for Jackson
        public Numbers() {}

        public Numbers(double a, double b) {
            this.a = a;
            this.b = b;
        }
    }
}

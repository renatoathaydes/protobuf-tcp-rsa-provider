package com.athaydes.protobuf.tcp;

import com.athaydes.protobuf.tcp.api.Api;
import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;

/**
 * Base class for tests that check socket communication.
 */
public abstract class TestsCommunication {


    static final int EXAMPLE_SERVICE_PORT = 5556;
    static final int JAVA_SERVICE_PORT = 5557;
    static final int RUNNER_SERVICE_PORT = 5558;

    final ExecutorService serverThread = Executors.newSingleThreadExecutor();

    static final Api.MethodInvocation exampleMethodInvocation = Api.MethodInvocation.newBuilder()
            .setMethodName("serverMethod")
            .addArgs(Any.pack(StringValue.newBuilder().setValue("some-arg").build()))
            .build();

    final ServerClientTest.ExampleService exampleService = new ServerClientTest.ExampleService();

    final ServerClientTest.JavaService javaService = (prefix, a, b, showPrefix) ->
            (showPrefix ? prefix : "") + (a + b);

    final ServerClientTest.Runner runnerService = new ServerClientTest.Runner();

    final ProtobufServer<?> exampleServer = new ProtobufServer<>(exampleService, EXAMPLE_SERVICE_PORT);
    final ProtobufServer<?> javaServer = new ProtobufServer<>(javaService, JAVA_SERVICE_PORT);
    final ProtobufServer<?> runnerServer = new ProtobufServer<>(runnerService, RUNNER_SERVICE_PORT);

    @After
    public void cleanup() {
        exampleServer.close();
        javaServer.close();
        runnerServer.close();
        serverThread.shutdownNow();
    }

    static void waitForSocketToBind(int port) throws Exception {
        long giveupTime = System.currentTimeMillis() + 5000;
        while (true) {
            try {
                Socket socket = new Socket("127.0.0.1", port);
                socket.getOutputStream();
                System.out.println("Successfully connected to server");
                socket.close();
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() > giveupTime) {
                    throw new RuntimeException("Timeout waiting for server to bind to port " + port);
                }
                Thread.sleep(250L);
            }
        }
    }

    public interface Service {
        Api.MethodInvocation call(StringValue text);
    }

    public interface JavaService {
        String sum(String prefix, int a, float b, boolean showPrefix);
    }

    public static class ExampleService implements Service {

        final Method callMethod;

        ExampleService() {
            try {
                callMethod = ExampleService.class.getMethod("call", StringValue.class);
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError("Could not find the call method");
            }
        }

        final Deque<String> calls = new ConcurrentLinkedDeque<>();

        @Override
        public Api.MethodInvocation call(StringValue text) {
            calls.add(text.getValue());
            return exampleMethodInvocation;
        }

    }

    public static class Runner implements Runnable {

        final AtomicInteger methodCount = new AtomicInteger();

        @Override
        public void run() {
            methodCount.incrementAndGet();
        }
    }

}

package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.net.URI;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ServerClientTest {

    private static final int EXAMPLE_SERVICE_PORT = 5556;
    private static final int JAVA_SERVICE_PORT = 5557;
    private static final int RUNNER_SERVICE_PORT = 5558;

    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();

    private static final Api.MethodInvocation exampleMethodInvocation = Api.MethodInvocation.newBuilder()
            .setMethodName("serverMethod")
            .addArgs(Any.pack(StringValue.newBuilder().setValue("some-arg").build()))
            .build();

    private final ExampleService exampleService = new ExampleService();

    private final JavaService javaService = (prefix, a, b, showPrefix) ->
            (showPrefix ? prefix : "") + (a + b);

    private final Runner runnerService = new Runner();

    private final ProtobufServer exampleServer = new ProtobufServer(EXAMPLE_SERVICE_PORT, exampleService);
    private final ProtobufServer javaServer = new ProtobufServer(JAVA_SERVICE_PORT, javaService);
    private final ProtobufServer runnerServer = new ProtobufServer(RUNNER_SERVICE_PORT, runnerService);

    @After
    public void cleanup() {
        exampleServer.close();
        javaServer.close();
        runnerServer.close();
        serverThread.shutdownNow();
    }

    @Test
    public void testServerClient() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + EXAMPLE_SERVICE_PORT));

        // make a RPC call
        Object result = handler.invoke(exampleService, exampleService.callMethod,
                new Object[]{StringValue.newBuilder().setValue("hello").build()});

        // ensure the result is correct
        assertThat(result, equalTo(exampleMethodInvocation));

        // ensure the method was really called
        String arg = exampleService.calls.removeFirst();
        assertThat(arg, equalTo("hello"));
    }

    @Test
    public void testProxyClient() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + EXAMPLE_SERVICE_PORT));

        // wrap handler into a Proxy
        Service proxyService = (Service) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{Service.class}, handler);

        // make a indirect RPC call using the proxy
        Object result = proxyService.call(StringValue.newBuilder().setValue("bye").build());

        // ensure the result is correct
        assertThat(result, equalTo(exampleMethodInvocation));

        // ensure the method was really called
        String arg = exampleService.calls.removeFirst();
        assertThat(arg, equalTo("bye"));
    }

    @Test
    public void testProxyClientWithJavaServiceRepeatedCalls() throws Throwable {
        // start the server
        serverThread.submit(javaServer);

        // server should be running now
        waitForSocketToBind(JAVA_SERVICE_PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + JAVA_SERVICE_PORT));

        // wrap handler into a Proxy
        JavaService proxyService = (JavaService) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{JavaService.class}, handler);

        // make a indirect RPC call using the proxy
        String result = proxyService.sum("Sum = ", 2, 3.25f, true);

        // ensure the result is correct
        assertThat(result, equalTo("Sum = 5.25"));

        // second call
        result = proxyService.sum("", 1, 0.5f, false);

        // ensure the result is correct
        assertThat(result, equalTo("1.5"));

        // second call
        result = proxyService.sum("Result:", -1, -0.5f, true);

        // ensure the result is correct
        assertThat(result, equalTo("Result:-1.5"));
    }

    @Test
    public void testRunnerServiceReturningVoid() throws Throwable {
        // start the server
        serverThread.submit(runnerServer);

        // server should be running now
        waitForSocketToBind(RUNNER_SERVICE_PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + RUNNER_SERVICE_PORT));

        // wrap handler into a Proxy
        Runnable proxyService = (Runnable) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{Runnable.class}, handler);

        // make a few indirect RPC calls using the proxy
        proxyService.run();
        proxyService.run();
        proxyService.run();

        // ensure the method was really called
        assertThat(runnerService.methodCount.get(), equalTo(3));
    }

    private static void waitForSocketToBind(int port) throws Exception {
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

        private final AtomicInteger methodCount = new AtomicInteger();

        @Override
        public void run() {
            methodCount.incrementAndGet();
        }
    }

}

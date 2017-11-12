package com.athaydes.protobuf.tcp;

import com.google.protobuf.StringValue;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.stream.LongStream;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ServerClientTest extends TestsCommunication {

    @Test
    public void testServerClient() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        try (ProtobufInvocationHandler handler = new ProtobufInvocationHandler(
                URI.create("tcp://127.0.0.1:" + EXAMPLE_SERVICE_PORT))) {
            // make a RPC call
            Object result = handler.invoke(exampleService, exampleService.callMethod,
                    new Object[]{StringValue.newBuilder().setValue("hello").build()});

            // ensure the result is correct
            assertThat(result, equalTo(exampleMethodInvocation));

            // ensure the method was really called
            String arg = exampleService.calls.removeFirst();
            assertThat(arg, equalTo("hello"));
        }
    }

    @Test
    public void testProxyClient() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        try (ProtobufInvocationHandler handler = new ProtobufInvocationHandler(
                URI.create("tcp://127.0.0.1:" + EXAMPLE_SERVICE_PORT))) {
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
    }

    @Test
    public void testProxyClientWithJavaServiceRepeatedCalls() throws Throwable {
        // start the server
        serverThread.submit(javaServer);

        // server should be running now
        waitForSocketToBind(JAVA_SERVICE_PORT);

        try (ProtobufInvocationHandler handler = new ProtobufInvocationHandler(
                URI.create("tcp://127.0.0.1:" + JAVA_SERVICE_PORT))) {
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
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testRunnerServiceReturningVoid() throws Throwable {
        // start the server
        runnerServer.run();

        // server should be running now
        waitForSocketToBind(RUNNER_SERVICE_PORT);

        try (ProtobufInvocationHandler handler = new ProtobufInvocationHandler(
                URI.create("tcp://127.0.0.1:" + RUNNER_SERVICE_PORT))) {
            // wrap handler into a Proxy
            Runnable proxyService = (Runnable) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                    new Class[]{Runnable.class}, handler);

            long[] times = new long[3];
            long startTime = System.nanoTime();

            // make a few indirect RPC calls using the proxy
            proxyService.run();
            times[0] = System.nanoTime() - startTime;
            startTime = System.nanoTime();
            proxyService.run();
            times[1] = System.nanoTime() - startTime;
            startTime = System.nanoTime();
            proxyService.run();
            times[2] = System.nanoTime() - startTime;

            // show how long the RPC calls took
            System.out.printf("3 RPC calls times (ns): %s\nAverage: %2f ns",
                    Arrays.toString(times), LongStream.of(times).average().getAsDouble());

            // ensure the method was really called
            assertThat(runnerService.methodCount.get(), equalTo(3));
        }
    }

}

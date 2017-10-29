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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ServerClientTest {

    private static final int PORT = 5556;

    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();

    private static final Api.MethodInvocation exampleMethodInvocation = Api.MethodInvocation.newBuilder()
            .setMethodName("serverMethod")
            .addArgs(Any.pack(StringValue.newBuilder().setValue("some-arg").build()))
            .build();

    private final ExampleService service = new ExampleService();
    private final ProtobufServer server = new ProtobufServer(PORT, service);

    @After
    public void cleanup() {
        server.close();
        serverThread.shutdownNow();
    }

    @Test
    public void testServerClient() throws Throwable {
        // start the server
        serverThread.submit(server);

        // server should be running now
        waitForSocketToBind(PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + PORT));

        // make a RPC call
        Object result = handler.invoke(service, service.callMethod,
                new Object[]{StringValue.newBuilder().setValue("hello").build()});

        // ensure the result is correct
        assertThat(result, equalTo(exampleMethodInvocation));

        // ensure the method was really called
        String arg = service.calls.removeFirst();
        assertThat(arg, equalTo("hello"));
    }

    @Test
    public void testProxyClient() throws Throwable {
        // start the server
        serverThread.submit(server);

        // server should be running now
        waitForSocketToBind(PORT);

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + PORT));

        // wrap handler into a Proxy
        Service proxyService = (Service) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                new Class[]{Service.class}, handler);

        // make a indirect RPC call using the proxy
        Object result = proxyService.call(StringValue.newBuilder().setValue("bye").build());

        // ensure the result is correct
        assertThat(result, equalTo(exampleMethodInvocation));

        // ensure the method was really called
        String arg = service.calls.removeFirst();
        assertThat(arg, equalTo("bye"));
    }

    private static void waitForSocketToBind(int port) throws Exception {
        long giveupTime = System.currentTimeMillis() + 5000;
        while (true) {
            try {
                Socket socket = new Socket("127.0.0.1", PORT);
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

}

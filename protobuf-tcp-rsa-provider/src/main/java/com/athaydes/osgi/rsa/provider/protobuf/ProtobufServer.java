package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.athaydes.osgi.rsa.provider.protobuf.Utils.closeQuietly;

/**
 * A TCP implementation of a Protobuf RPC server that sends method invocations to a local service.
 */
public class ProtobufServer implements Runnable, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ProtobufServer.class);

    private final int port;
    private final Object service;
    private final AtomicReference<ServerSocket> serverSocketRef = new AtomicReference<>();

    private final ExecutorService handlerService = Executors.newFixedThreadPool(5);

    ProtobufServer(int port, Object service) {
        this.port = port;
        this.service = service;
    }

    @Override
    public void run() {
        log.info("Starting ProtobufServer on port " + port);

        ServerSocket serverSocket;
        try {
            if (!serverSocketRef.compareAndSet(null, serverSocket = new ServerSocket(port))) {
                throw new RuntimeException("Server already running");
            }
        } catch (IOException e) {
            log.warn("Error starting server", e);
            throw new RuntimeException(e);
        }

        boolean running = true;

        while (running) {
            log.debug("Waiting for new client");
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
                log.debug("Accepting connection from: {}", clientSocket.getInetAddress());
                handlerService.submit(new Handler(service, clientSocket));
            } catch (IOException e) {
                log.warn("Error while handing over client to worker", e);
                running = false;
            } catch (RejectedExecutionException e) {
                log.warn("This service has been stopped and cannot respond anymore!");
                if (clientSocket != null) {
                    closeQuietly(clientSocket);
                }
                running = false;
            }
            if (Thread.currentThread().isInterrupted()) {
                log.info("Server Thread has been interrupted, server no longer listening");
                running = false;
            }
        }
    }

    @Override
    public void close() {
        log.info("Stopping server");
        ServerSocket serverSocket = serverSocketRef.get();
        if (serverSocket != null) {
            closeQuietly(serverSocket);
        }
        handlerService.shutdown();
    }

    private static class Handler implements Runnable {

        private final Object service;
        private final Socket clientSocket;

        Handler(Object service,
                Socket clientSocket) {
            this.service = service;
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            Api.MethodInvocation message;
            OutputStream out = null;
            try {
                out = clientSocket.getOutputStream();
                message = Api.MethodInvocation.parseDelimitedFrom(clientSocket.getInputStream());
            } catch (IOException e) {
                log.warn("Error parsing message", e);
                if (out != null) {
                    sendError(e, out);
                }
                return;
            }

            String methodName = message.getMethodName();
            List<Any> args = message.getArgsList();

            log.debug("Looking up method '{}' of service {}", methodName, service);

            Optional<Method> method = Arrays.stream(service.getClass().getMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .filter(m -> matchesArgTypes(m, args.iterator()))
                    .findAny();

            if (method.isPresent()) {
                Method selectedMethod = method.get();
                log.debug("Found method: {}", selectedMethod);
                try {
                    Object result = selectedMethod.invoke(service,
                            unpack(args.iterator(), selectedMethod.getParameterTypes()));
                    sendResult(result, out);
                    log.debug("Successfully processed method invocation");
                } catch (IllegalAccessException | InvocationTargetException e) {
                    sendError(e, out);
                }
            } else {
                log.debug("Method not found");
                sendError(new NoSuchMethodException(methodName), out);
            }
        }

        private void sendResult(Object result, OutputStream out) {
            if (result instanceof String) {
                log.debug("Wrapping String return value into StringValue");
                result = StringValue.newBuilder()
                        .setValue(result.toString())
                        .build();
            }
            if (result instanceof Message) {
                log.debug("Method invocation returned value: {}", result);
                try {
                    Api.Result.newBuilder().setSuccessResult(
                            Any.pack((Message) result)
                    ).build().writeDelimitedTo(out);
                    out.flush();
                } catch (IOException e) {
                    log.warn("Error writing success response", e);
                    closeQuietly(out);
                }
            } else {
                sendError(new IllegalStateException("Return type is not a valid RPC type: " + result), out);
            }
        }

        private void sendError(Exception e, OutputStream out) {
            try {
                Api.Result.newBuilder().setException(Api.Exception.newBuilder()
                        .setType(e.getClass().getName())
                        .setMessage(e.getMessage())
                        .build()).build().writeDelimitedTo(out);
                out.flush();
            } catch (IOException e1) {
                log.warn("Error writing response", e1);
                closeQuietly(out);
            }
        }

        @SuppressWarnings("unchecked")
        private static Object[] unpack(Iterator<Any> args, Class[] parameterTypes) {
            Object[] result = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                try {
                    Class type = parameterTypes[i];
                    boolean unwrap = false;
                    if (type.equals(String.class)) {
                        type = StringValue.class;
                        unwrap = true;
                    }
                    Message unpackedValue = args.next().unpack(type);
                    if (unwrap) {
                        result[i] = ((StringValue) unpackedValue).getValue();
                    } else {
                        result[i] = unpackedValue;
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
            return result;
        }

        private static boolean matchesArgTypes(Method method, Iterator<Any> args) {
            Class[] argTypes = method.getParameterTypes();
            for (Class<?> paramType : argTypes) {
                if (paramType.equals(String.class)) {
                    paramType = StringValue.class;
                }
                if (Message.class.isAssignableFrom(paramType)) {
                    Class<? extends Message> messageType = paramType.asSubclass(Message.class);
                    if (args.hasNext()) {
                        Any argType = args.next();
                        boolean match = argType.is(messageType);
                        if (!match) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            return true;
        }

    }

    public static void main(String[] args) {
        class HelloService {
            public StringValue sayHello(StringValue message) {
                return StringValue.newBuilder()
                        .setValue("Hello " + message.getValue())
                        .build();
            }
        }

        System.out.println("Starting ProtobufServer with HelloService.\n" +
                "The only method call allowed has the following signature:\n" +
                "  public StringValue sayHello(StringValue message)\n" +
                "Send a message to port 5556 and this server will respond!\n");

        ProtobufServer server = new ProtobufServer(5556, new HelloService());

        server.run();
    }

}

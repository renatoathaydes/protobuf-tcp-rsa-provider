package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.google.protobuf.Any;
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
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.athaydes.osgi.rsa.provider.protobuf.MethodResolver.resolveMethods;
import static com.athaydes.osgi.rsa.provider.protobuf.Utils.closeQuietly;
import static java.util.Collections.emptyList;

/**
 * A TCP implementation of a Protobuf RPC server that sends method invocations to a local service.
 */
public class ProtobufServer implements Runnable, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ProtobufServer.class);

    private final int port;
    private final Object service;
    private final AtomicReference<ServerSocket> serverSocketRef = new AtomicReference<>();
    private final Map<String, List<Method>> methodsByName;

    private final ExecutorService handlerService = Executors.newFixedThreadPool(5);

    public ProtobufServer(int port, Object service) {
        this(port, service, new Class[]{});
    }

    public ProtobufServer(int port, Object service, Class[] exportedInterfaces) {
        this.port = port;
        this.service = service;
        this.methodsByName = resolveMethods(service, exportedInterfaces);
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
                handlerService.submit(new Handler(service, methodsByName, clientSocket));
            } catch (SocketException e) {
                log.info("Client disconnected: {}", e.getMessage());
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
        private final Map<String, List<Method>> methodsByName;
        private final Socket clientSocket;

        Handler(Object service,
                Map<String, List<Method>> methodsByName,
                Socket clientSocket) {
            this.service = service;
            this.methodsByName = methodsByName;
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

            Optional<MethodInvocationResolver.ResolvedInvocationInfo> resolvedInvocationInfo = methodsByName
                    .getOrDefault(methodName, emptyList()).stream()
                    .map(m -> MethodInvocationResolver.resolveMethodInvocation(m, args))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findAny();

            if (resolvedInvocationInfo.isPresent()) {
                log.debug("Resolved method invocation: {}", resolvedInvocationInfo.get());
                try {
                    Any result = resolvedInvocationInfo.get().callWith(service);
                    sendResult(result, out);
                    log.debug("Successfully processed method invocation");
                } catch (InvocationTargetException e) {
                    sendError(e.getCause(), out);
                } catch (Exception e) {
                    sendError(e, out);
                }
            } else {
                log.debug("Method not found");
                sendError(new NoSuchMethodException(methodName), out);
            }
        }

        private void sendResult(Any result, OutputStream out) {
            log.debug("Method invocation returned value: {}", result);
            try {
                Api.Result.newBuilder().setSuccessResult(result).build()
                        .writeDelimitedTo(out);
                out.flush();
            } catch (IOException e) {
                log.warn("Error writing success response", e);
                closeQuietly(out);
            }
        }

        private void sendError(Throwable e, OutputStream out) {
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
                "Send a message to port 5562 and this server will respond!\n");

        ProtobufServer server = new ProtobufServer(5562, new HelloService());

        server.run();
    }

}

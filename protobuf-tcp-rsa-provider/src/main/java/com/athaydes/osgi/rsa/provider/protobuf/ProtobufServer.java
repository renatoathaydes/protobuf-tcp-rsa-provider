package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<AsynchronousServerSocketChannel> serverSocketRef = new AtomicReference<>();
    private final Map<String, List<Method>> methodsByName;

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

        AsynchronousServerSocketChannel serverSocket;
        try {
            if (!serverSocketRef.compareAndSet(null, serverSocket = AsynchronousServerSocketChannel.open())) {
                throw new RuntimeException("Server already running");
            }
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
        } catch (IOException e) {
            log.warn("Error starting server", e);
            throw new RuntimeException(e);
        }

        log.info("Accepting client connections");
        running.set(true);

        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientSocket,
                                  Void ignore) {
                if (running.get()) {
                    try {
                        log.debug("Accepting connection from: {}", clientSocket.getRemoteAddress());
                        serverSocket.accept(null, this);
                        new Handler(service, methodsByName, clientSocket).run();
                    } catch (IOException e) {
                        log.warn("Unable to get client remote address");
                        closeQuietly(clientSocket);
                    }
                } else {
                    closeQuietly(serverSocket);
                }
            }

            @Override
            public void failed(Throwable exc, Void serverSocket) {
                if (exc instanceof AsynchronousCloseException) {
                    log.debug("Client closed connection");
                } else {
                    log.warn("Failed to accept client socket", exc);
                }
            }
        });
    }

    @Override
    public void close() {
        log.info("Stopping server");
        running.set(false);
        AsynchronousServerSocketChannel serverSocket = serverSocketRef.get();
        if (serverSocket != null) {
            closeQuietly(serverSocket);
        }
    }

    private static class Handler implements CompletionHandler<Integer, ByteBuffer> {

        private static final int MESSAGE_LENGTH = 1;

        private final Object service;
        private final Map<String, List<Method>> methodsByName;
        private final AsynchronousSocketChannel clientSocket;
        private final AtomicInteger bytesReceived = new AtomicInteger(0);

        Handler(Object service,
                Map<String, List<Method>> methodsByName,
                AsynchronousSocketChannel clientSocket) {
            this.service = service;
            this.methodsByName = methodsByName;
            this.clientSocket = clientSocket;
        }

        void run() {
            ByteBuffer lengthBuffer = allocateLengthBuffer();
            clientSocket.read(lengthBuffer, 5, TimeUnit.SECONDS, lengthBuffer, this);
        }

        private static ByteBuffer allocateLengthBuffer() {
            return ByteBuffer.allocate(MESSAGE_LENGTH);
        }

        @Override
        public void completed(Integer bytesCount, ByteBuffer lengthBuffer) {
            if (bytesCount < 0) {
                log.debug("Received bytesCount = {}, closing client socket", bytesCount);
                closeQuietly(clientSocket);
                return;
            }
            int received = bytesReceived.addAndGet(bytesCount);

            if (received < MESSAGE_LENGTH) {
                log.debug("Received {} bytes so far, waiting for a total of {}.", received, MESSAGE_LENGTH);
                clientSocket.read(lengthBuffer, 5, TimeUnit.SECONDS, lengthBuffer, this);
                return;
            }

            lengthBuffer.flip();
            int messageLength;
            try {
                messageLength = CodedInputStream.newInstance(lengthBuffer).readRawVarint32();
            } catch (IOException e) {
                // should never happen, the stream is backed by a memory buffer
                sendError(e);
                return;
            }
            log.debug("Expecting message with length {}", messageLength);
            if (messageLength <= 0) {
                sendError(new IllegalArgumentException("Invalid message length"));
                return;
            }
            ByteBuffer msgBuffer = ByteBuffer.allocate(messageLength);
            clientSocket.read(msgBuffer, 10, TimeUnit.SECONDS, msgBuffer, new ServiceMethodInvoker(messageLength));
        }

        @Override
        public void failed(Throwable exc, ByteBuffer lengthBuffer) {
            sendError(exc);
        }

        private void sendError(Throwable error) {
            Api.Result response = Api.Result.newBuilder().setException(Api.Exception.newBuilder()
                    .setType(error.getClass().getName())
                    .setMessage(Optional.ofNullable(error.getMessage()).orElse(""))
                    .build()).build();

            sendResult(response);
        }

        private void sendResult(Api.Result result) {
            int resultLength = result.getSerializedSize();
            ByteBuffer buffer = ByteBuffer.allocate(resultLength + 4);
            try {
                CodedOutputStream.newInstance(buffer).writeRawVarint32(resultLength);
                log.debug("Sending result to client: {}", result);
                result.writeTo(CodedOutputStream.newInstance(buffer));
                ByteArrayOutputStream out = new ByteArrayOutputStream(result.getSerializedSize() + 4);
                result.writeDelimitedTo(out);
                clientSocket.write(ByteBuffer.wrap(out.toByteArray()));
            } catch (IOException ignore) {
                // never happens, write is async
            } finally {
                acceptNewInvocation();
            }
        }

        private void acceptNewInvocation() {
            bytesReceived.set(0);
            ByteBuffer lengthBuffer = allocateLengthBuffer();
            clientSocket.read(lengthBuffer, 5, TimeUnit.SECONDS, lengthBuffer, Handler.this);
        }

        private class ServiceMethodInvoker implements CompletionHandler<Integer, ByteBuffer> {

            private final int messageLength;
            private final AtomicInteger bytesReceived = new AtomicInteger(0);

            ServiceMethodInvoker(int messageLength) {
                this.messageLength = messageLength;
            }

            @Override
            public void completed(Integer bytesCount, ByteBuffer msgBuffer) {
                if (bytesCount < 0) {
                    log.debug("Received bytesCount = {}, closing client socket", bytesCount);
                    closeQuietly(clientSocket);
                    return;
                }

                int received = bytesReceived.addAndGet(bytesCount);

                if (received < messageLength) {
                    log.debug("Received {} bytes so far, waiting for a total of {}.", received, messageLength);
                    clientSocket.read(msgBuffer, 5, TimeUnit.SECONDS, msgBuffer, this);
                    return;
                }

                log.debug("Received full message with length {}, parsing it.", messageLength);
                msgBuffer.flip();
                Api.MethodInvocation message;
                try {
                    message = Api.MethodInvocation.parseFrom(CodedInputStream.newInstance(msgBuffer));
                } catch (InvalidProtocolBufferException e) {
                    sendError(new RuntimeException("Could not parse message: " + e));
                    return;
                } catch (IOException e) {
                    // should not happen, the msgBuffer is read from the socket already
                    sendError(e);
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
                        sendResult(Api.Result.newBuilder().setSuccessResult(result).build());
                        log.debug("Successfully processed method invocation");
                    } catch (InvocationTargetException e) {
                        sendError(e.getCause());
                    } catch (Exception e) {
                        sendError(e);
                    }
                } else {
                    log.debug("Method not found");
                    sendError(new NoSuchMethodException(methodName));
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                sendError(exc);
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

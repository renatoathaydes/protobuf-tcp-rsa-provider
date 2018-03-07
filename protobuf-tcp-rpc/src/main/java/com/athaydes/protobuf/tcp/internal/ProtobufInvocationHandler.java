package com.athaydes.protobuf.tcp.internal;

import com.athaydes.protobuf.tcp.api.Api;
import com.athaydes.protobuf.tcp.api.Api.MethodInvocation;
import com.athaydes.protobuf.tcp.api.CommunicationException;
import com.athaydes.protobuf.tcp.api.RemoteException;
import com.athaydes.protobuf.tcp.internal.Internal.BooleanArray;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

/**
 * TCP Protobuf-based {@link InvocationHandler}.
 * <p>
 * This class can be used to create a {@link java.lang.reflect.Proxy} for a service
 * whose implementation is provided by a remote service.
 */
public class ProtobufInvocationHandler implements InvocationHandler, AutoCloseable {

    private static final Map<Class<?>, Function<Object, Any>> packFunctions;

    static {
        Map<Class<?>, Function<Object, Any>> packFunctions_ = new HashMap<>(9);

        packFunctions_.put(String.class, object ->
                Any.pack(StringValue.newBuilder().setValue((String) object).build()));
        packFunctions_.put(Character.class, object ->
                Any.pack(StringValue.newBuilder().setValue(object.toString()).build()));
        packFunctions_.put(Boolean.class, object ->
                Any.pack(BoolValue.newBuilder().setValue((boolean) object).build()));
        packFunctions_.put(Integer.class, object ->
                Any.pack(Int32Value.newBuilder().setValue((int) object).build()));
        packFunctions_.put(Short.class, object ->
                Any.pack(Int32Value.newBuilder().setValue((short) object).build()));
        packFunctions_.put(Long.class, object ->
                Any.pack(Int64Value.newBuilder().setValue((long) object).build()));
        packFunctions_.put(Float.class, object ->
                Any.pack(FloatValue.newBuilder().setValue((float) object).build()));
        packFunctions_.put(Double.class, object ->
                Any.pack(DoubleValue.newBuilder().setValue((double) object).build()));
        packFunctions_.put(Byte.class, object ->
                Any.pack(BytesValue.newBuilder().setValue(ByteString.copyFrom(new byte[]{(byte) object})).build()));
        packFunctions_.put(byte[].class, object -> {
            Internal.IntArray.Builder builder = Internal.IntArray.newBuilder();
            for (int i : (byte[]) object) {
                builder.addArray(i);
            }
            return Any.pack(builder.build());
        });
        packFunctions_.put(boolean[].class, object -> {
            BooleanArray.Builder builder = BooleanArray.newBuilder();
            for (boolean b : (boolean[]) object) {
                builder.addArray(b);
            }
            return Any.pack(builder.build());
        });
        packFunctions_.put(char[].class, object -> {
            StringValue.Builder builder = StringValue.newBuilder();
            builder.setValue(new String((char[]) object));
            return Any.pack(builder.build());
        });
        packFunctions_.put(short[].class, object -> {
            Internal.IntArray.Builder builder = Internal.IntArray.newBuilder();
            for (int i : (short[]) object) {
                builder.addArray(i);
            }
            return Any.pack(builder.build());
        });
        packFunctions_.put(int[].class, object -> {
            Internal.IntArray.Builder builder = Internal.IntArray.newBuilder();
            for (int i : (int[]) object) {
                builder.addArray(i);
            }
            return Any.pack(builder.build());
        });
        packFunctions_.put(long[].class, object -> {
            Internal.LongArray.Builder builder = Internal.LongArray.newBuilder();
            for (long l : (long[]) object) {
                builder.addArray(l);
            }
            return Any.pack(builder.build());
        });
        packFunctions_.put(double[].class, object -> {
            Internal.DoubleArray.Builder builder = Internal.DoubleArray.newBuilder();
            for (double d : (double[]) object) {
                builder.addArray(d);
            }
            return Any.pack(builder.build());
        });

        packFunctions = Collections.unmodifiableMap(packFunctions_);
    }

    private static final Logger log = LoggerFactory.getLogger(ProtobufInvocationHandler.class);

    private static final Method closeMethod;

    static {
        try {
            closeMethod = Closeable.class.getMethod("close");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError("Closeable interface does not have a close() method");
        }
    }

    private final URI address;
    private final AtomicReference<Socket> socketRef = new AtomicReference<>();
    private final boolean forwardCloseMethodCall;

    public ProtobufInvocationHandler(URI address) {
        this(address, false);
    }

    public ProtobufInvocationHandler(URI address, boolean forwardCloseMethodCall) {
        this.address = address;
        this.forwardCloseMethodCall = forwardCloseMethodCall;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws RuntimeException {
        if (closeMethod.equals(method)) {
            return handleCloseMethod();
        } else if (method.getDeclaringClass().equals(Object.class)) {
            return invokeLocalObjectMethod(proxy, method, args);
        } else {
            return callRemoteMethod(method, args);
        }
    }

    private Object invokeLocalObjectMethod(Object proxy, Method method, Object[] args) {
        log.info("Invoking local method {}", method);

        // implement only the Object methods that are overridable
        switch (method.getName()) {
            case "toString":
                return "RemoteService{address=" + address + ",class=" + proxy.getClass().getName() + "}";
            case "equals":
                return args[0] == proxy;
            case "hashCode":
                return proxy.getClass().hashCode() + address.hashCode();
            default:
                throw new RuntimeException(new NoSuchMethodException(method.getName()));
        }
    }

    private Object callRemoteMethod(Method method, Object[] args) {
        log.debug("Calling remote method '{}'", method.getName());
        MethodInvocation invocation = MethodInvocation.newBuilder()
                .setMethodName(method.getName())
                .addAllArgs(Arrays.stream(args == null ? new Object[]{} : args)
                        .map(ProtobufInvocationHandler::packedMessage)
                        .peek(msg -> {
                            if (msg == null) {
                                throw new NullPointerException("Remote method invocation cannot accept null argument");
                            }
                        }).collect(toList())).build();

        Api.Result result = null;
        int retries = 1;

        while (retries >= 0) {
            Socket socket = null;
            try {
                socket = createOrReuseSocket();
                log.debug("Connected to server {}, sending message with length: {}",
                        address, invocation.getSerializedSize());
                OutputStream out = socket.getOutputStream();
                invocation.writeDelimitedTo(out);
                out.flush();

                log.debug("Waiting for server response");
                result = Api.Result.parseDelimitedFrom(socket.getInputStream());
                if (result == null) {
                    log.debug("Received EOF, resetting the socket");
                    socketRef.set(null);
                } else {
                    break;
                }
            } catch (IOException e) {
                log.debug("Problem connecting to server [retries={}]: {}", retries, e.toString());
                socketRef.set(null);
                Optional.ofNullable(socket).ifPresent(Utils::closeQuietly);
                if (retries <= 0) {
                    throw new CommunicationException(e);
                }
            } finally {
                retries--;
            }
        }

        log.debug("Received result: {}", result);

        if (result == null) {
            return null;
        } else switch (result.getResultCase()) {
            case SUCCESSRESULT:
                if (method.getReturnType().equals(void.class)) {
                    return null;
                }
                try {
                    return MethodInvocationResolver.convert(result.getSuccessResult(), method.getReturnType());
                } catch (IOException e) {
                    throw new CommunicationException(e);
                }
            case EXCEPTION:
                throw new RemoteException(result.getException().getType(), result.getException().getMessage());
            default:
                return null;
        }
    }

    private Object handleCloseMethod() {
        if (forwardCloseMethodCall) try {
            callRemoteMethod(closeMethod, new Object[]{});
        } catch (CommunicationException e) {
            // swallow CommunicationException
            log.debug("Ignoring CommunicationException when closing client: {}", e.toString());
        }

        log.debug("Closing client");
        close();

        return null;
    }

    private Socket createOrReuseSocket() throws IOException {
        Socket oldSocket = socketRef.get();
        if (oldSocket != null) {
            log.debug("Reusing socket for {}", address);
            return oldSocket;
        } else {
            log.debug("Creating new socket for {}", address);
            Socket newSocket = new Socket(address.getHost(), address.getPort());
            socketRef.set(newSocket);
            return newSocket;
        }
    }

    /**
     * Converts an object to a Protobuf message wrapped into {@link Any}.
     *
     * @param object to convert
     * @return converted object if possible. If object is null, null is returned.
     * @throws IllegalArgumentException if a conversion is not possible.
     */
    static Any packedMessage(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof Message) {
            return Any.pack((Message) object);
        }

        Function<Object, Any> packFun = packFunctions.get(object.getClass());

        if (packFun != null) {
            return packFun.apply(object);
        }

        throw new IllegalArgumentException("Cannot pack " + object.getClass() + " into protobuff message");
    }

    @Override
    public void close() {
        Optional.ofNullable(socketRef.getAndSet(null)).ifPresent(Utils::closeQuietly);
    }

}

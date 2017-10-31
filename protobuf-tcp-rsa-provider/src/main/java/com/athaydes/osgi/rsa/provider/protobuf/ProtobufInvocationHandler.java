package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.athaydes.osgi.rsa.provider.protobuf.api.Api.MethodInvocation;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;

import static java.util.stream.Collectors.toList;

/**
 * TCP Protobuf-based {@link InvocationHandler}.
 */
public class ProtobufInvocationHandler implements InvocationHandler, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProtobufInvocationHandler.class);

    private final URI address;

    ProtobufInvocationHandler(URI address) {
        this.address = address;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws RuntimeException {
        MethodInvocation invocation = MethodInvocation.newBuilder()
                .setMethodName(method.getName())
                .addAllArgs(Arrays.stream(args == null ? new Object[]{} : args)
                        .map(ProtobufInvocationHandler::packedMessage)
                        .peek(msg -> {
                            if (msg == null) {
                                throw new NullPointerException("Remote method invocation cannot accept null argument");
                            }
                        }).collect(toList())).build();

        Api.Result result;

        try (Socket socket = new Socket(address.getHost(), address.getPort())) {
            log.debug("Connected to server {}, sending data", address);
            OutputStream out = socket.getOutputStream();
            invocation.writeDelimitedTo(out);
            out.flush();

            log.debug("Waiting for server response");
            result = Api.Result.parseDelimitedFrom(socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("Received result: {}", result);

        if (result == null) {
            return null;
        } else switch (result.getResultCase()) {
            case SUCCESSRESULT:
                if (method.getReturnType().equals(void.class)) {
                    return null;
                }
                Object value = MethodInvocationResolver.tryConvert(result.getSuccessResult(), method.getReturnType());
                if (value == null) {
                    throw new RuntimeException(String.format("Cannot convert %s to %s",
                            result.getSuccessResult().getTypeUrl(), method.getReturnType()));
                } else {
                    return value;
                }
            case EXCEPTION:
                throw new RuntimeException(String.format("RemoteException of type %s: %s",
                        result.getException().getType(), result.getException().getMessage()));
            default:
                return null;
        }
    }

    /**
     * Converts an object to a Protobuf message wrapped into {@link Any}.
     *
     * @param object to convert
     * @return converted object if possible. If object is null, null is returned.
     * @throws ClassCastException if a conversion is not possible.
     */
    static Any packedMessage(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof Message) {
            return Any.pack((Message) object);
        }
        if (object instanceof String || object instanceof Character) {
            return Any.pack(StringValue.newBuilder().setValue(object.toString()).build());
        }
        if (object instanceof Boolean) {
            return Any.pack(BoolValue.newBuilder().setValue((boolean) object).build());
        }
        if (object instanceof Integer || object instanceof Short) {
            return Any.pack(Int32Value.newBuilder().setValue(((Number) object).intValue()).build());
        }
        if (object instanceof Long) {
            return Any.pack(Int64Value.newBuilder().setValue((long) object).build());
        }
        if (object instanceof Float) {
            return Any.pack(FloatValue.newBuilder().setValue((float) object).build());
        }
        if (object instanceof Double) {
            return Any.pack(DoubleValue.newBuilder().setValue((double) object).build());
        }
        if (object instanceof Byte) {
            return Any.pack(BytesValue.newBuilder().setValue(ByteString.copyFrom(new byte[]{(byte) object})).build());
        }

        throw new ClassCastException("Cannot cast " + object.getClass() + " to " + Any.class);
    }

    @Override
    public void close() {
        // TODO keep socket around then close it here
    }

}

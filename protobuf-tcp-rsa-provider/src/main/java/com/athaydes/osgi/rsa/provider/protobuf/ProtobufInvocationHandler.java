package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.osgi.rsa.api.Api;
import com.athaydes.osgi.rsa.api.Api.MethodInvocation;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

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
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodInvocation invocation = MethodInvocation.newBuilder()
                .setMethodName(method.getName())
                .addAllArgs(Arrays.stream(args)
                        .map(ProtobufInvocationHandler::toMessage)
                        .map(Any::pack)
                        .collect(Collectors.toList()))
                .build();

        Api.Result result;

        try (Socket socket = new Socket(address.getHost(), address.getPort())) {
            log.debug("Connected to server {}, sending data", address);
            OutputStream out = socket.getOutputStream();
            invocation.writeDelimitedTo(out);
            out.flush();

            log.debug("Waiting for server response");
            result = Api.Result.parseDelimitedFrom(socket.getInputStream());
        }

        log.debug("Received result: {}", result);

        if (result == null) {
            return null;
        } else switch (result.getResultCase()) {
            case SUCCESSRESULT:
                return result.getSuccessResult().unpack(method.getReturnType().asSubclass(Message.class));
            case EXCEPTION:
                throw new RuntimeException(result.getException().getType() +
                        ": " + result.getException().getMessage());
            default:
                return null;
        }
    }

    private static Message toMessage(Object object) {
        if (object instanceof Message) {
            return (Message) object;
        }
        if (object instanceof String) {
            return Any.pack(StringValue.newBuilder().setValue((String) object).build());
        }

        throw new ClassCastException("Cannot cast " + object.getClass() + " to " + MessageLite.class);
    }

    @Override
    public void close() {
        // TODO keep socket around then close it here
    }

}

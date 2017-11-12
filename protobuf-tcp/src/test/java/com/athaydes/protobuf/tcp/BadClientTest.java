package com.athaydes.protobuf.tcp;

import com.athaydes.protobuf.tcp.api.Api;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.InvalidProtocolBufferException;
import java.net.Socket;
import java.nio.channels.InterruptedByTimeoutException;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class BadClientTest extends TestsCommunication {

    @Test
    public void testUnparsableMessageSent() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        try (Socket socket = new Socket("127.0.0.1", EXAMPLE_SERVICE_PORT)) {
            byte[] gibberish = new byte[]{
                    // message length
                    5,
                    // gibberish
                    4, 1, 3, 1, 0
            };
            socket.getOutputStream().write(gibberish);

            Api.Result result = Api.Result.parseDelimitedFrom(socket.getInputStream());

            assertThat(result.getResultCase(), equalTo(Api.Result.ResultCase.EXCEPTION));
            assertThat(result.getException().getType(), equalTo(InvalidProtocolBufferException.class.getName()));
        }
    }

    @Test
    public void testIncompleteMessageSent() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        try (Socket socket = new Socket("127.0.0.1", EXAMPLE_SERVICE_PORT)) {
            byte[] boolBytes = BoolValue.getDefaultInstance().toByteArray();
            byte[] message = new byte[boolBytes.length + 1];
            message[0] = (byte) (boolBytes.length + 6); // send longer length
            System.arraycopy(boolBytes, 0, message, 1, boolBytes.length);

            socket.getOutputStream().write(message);

            Api.Result result = Api.Result.parseDelimitedFrom(socket.getInputStream());

            assertThat(result.getResultCase(), equalTo(Api.Result.ResultCase.EXCEPTION));
            assertThat(result.getException().getType(), equalTo(InterruptedByTimeoutException.class.getName()));
        }
    }

    @Test
    public void testCallNonExistingMethod() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        Api.MethodInvocation invocation = Api.MethodInvocation.newBuilder()
                .setMethodName("nonexistent")
                .build();

        try (Socket socket = new Socket("127.0.0.1", EXAMPLE_SERVICE_PORT)) {

            // send invocation of a method that does not exist
            invocation.writeDelimitedTo(socket.getOutputStream());

            Api.Result result = Api.Result.parseDelimitedFrom(socket.getInputStream());

            assertThat(result.getResultCase(), equalTo(Api.Result.ResultCase.EXCEPTION));
            assertThat(result.getException().getType(), equalTo(NoSuchMethodException.class.getName()));
            assertThat(result.getException().getMessage(), equalTo("nonexistent"));
        }
    }

    @Test
    public void testCallExistingMethodWithBadTypeArgs() throws Throwable {
        // start the server
        serverThread.submit(exampleServer);

        // server should be running now
        waitForSocketToBind(EXAMPLE_SERVICE_PORT);

        Api.MethodInvocation invocation = Api.MethodInvocation.newBuilder()
                .setMethodName("call")
                // send a boolean when a stringValue is expected
                .addArgs(Any.pack(BoolValue.getDefaultInstance()))
                .build();

        try (Socket socket = new Socket("127.0.0.1", EXAMPLE_SERVICE_PORT)) {

            // send invocation of a method that does not exist
            invocation.writeDelimitedTo(socket.getOutputStream());

            Api.Result result = Api.Result.parseDelimitedFrom(socket.getInputStream());

            assertThat(result.getResultCase(), equalTo(Api.Result.ResultCase.EXCEPTION));
            assertThat(result.getException().getType(), equalTo(NoSuchMethodException.class.getName()));
            assertThat(result.getException().getMessage(), equalTo("call"));
        }
    }

}

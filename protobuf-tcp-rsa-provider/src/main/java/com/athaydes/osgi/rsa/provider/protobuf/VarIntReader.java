package com.athaydes.osgi.rsa.provider.protobuf;

import com.google.protobuf.CodedInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Reads a varint32 using a one-byte ByteBuffer.
 */
final class VarIntReader {

    /**
     * Receives one byte at a time.
     */
    private final ByteBuffer receiver = ByteBuffer.allocate(1);

    /**
     * Accumulates bytes received.
     */
    private final ByteBuffer lengthBuffer = ByteBuffer.allocate(4);

    ByteBuffer buffer() {
        return receiver;
    }

    OptionalInt read() throws IOException {
        byte b = receiver.get(0);
        lengthBuffer.put(b);

        // check if this is the last byte of the varint32
        if ((b & 0x80) == 0) {
            lengthBuffer.flip();
            return OptionalInt.of(
                    CodedInputStream.newInstance(lengthBuffer).readRawVarint32());
        }

        if (lengthBuffer.position() >= 4) {
            throw new IOException("Incorrect varint32 encoding");
        } else {
            receiver.flip();
            return OptionalInt.empty();
        }
    }

}

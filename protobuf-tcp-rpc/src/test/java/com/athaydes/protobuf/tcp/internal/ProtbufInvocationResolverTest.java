package com.athaydes.protobuf.tcp.internal;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ProtbufInvocationResolverTest {

    @Test
    public void canConvertObjectToMessageAndBack() {
        StringValue stringValue = StringValue.newBuilder().setValue("hi").build();
        Int32Value int32Value = Int32Value.newBuilder().setValue(42).build();
        Int64Value int64Value = Int64Value.newBuilder().setValue(10L).build();
        BytesValue bytesValue = BytesValue.newBuilder().setValue(ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5})).build();

        String javaString = "ho";
        int javaInt = 56;
        char javaChar = 'a';
        boolean javaBool = true;
        short javaShort = 4;
        long javaLong = 30L;
        double javaDouble = 0.4;
        float javaFloat = 4.3f;
        byte javaByte = 0b01100110;

        List<Object> values = Arrays.asList(stringValue, int32Value, int64Value, bytesValue, javaString,
                javaInt, javaChar, javaBool, javaShort, javaLong, javaDouble, javaFloat, javaByte);

        final int repetitions = 10;

        List<Any> packedMessages = new ArrayList<>(values.size());
        long[] packingTimes = new long[values.size() * repetitions];

        int index = 0;

        // do it 10 times to measure the performance
        for (int runIndex = 0; runIndex < repetitions; runIndex++) {
            packedMessages.clear();

            // convert all values to packed messages
            for (Object value : values) {
                try {
                    long startTime = System.nanoTime();
                    packedMessages.add(ProtobufInvocationHandler.packedMessage(value));
                    packingTimes[index++] = System.nanoTime() - startTime;
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Error packing message: " + e);
                }
            }
        }

        // sanity check
        assertEquals(values.size(), packedMessages.size());

        long[] unpackingTimes = new long[values.size() * repetitions];
        index = 0;

        for (int j = 0; j < repetitions; j++) {
            // verify all packed messages have the correct type and value after unpacking
            for (int i = 0; i < packedMessages.size(); i++) {
                Any packedMessage = packedMessages.get(i);
                Object expectedValue = values.get(i);
                long startTime = System.nanoTime();

                // unpack
                Object unpacked = MethodInvocationResolver.tryConvert(packedMessage, expectedValue.getClass());
                unpackingTimes[index++] = System.nanoTime() - startTime;

                // verify value
                assertEquals(expectedValue, unpacked);
            }
        }

        // print performance report
        System.out.println("packedMessage() performance (ns): " + Arrays.toString(packingTimes));
        System.out.println("Average without 5 max outliers (ns): " + avg(packingTimes));
        System.out.println("tryConvert() performance (ns): " + Arrays.toString(unpackingTimes));
        System.out.println("Average without 5 max outliers (ns): " + avg(unpackingTimes));
    }

    @Test
    public void canConvertArraysToMessageAndBack() {
        byte[] javaByteArray = {3, 2, 1};
        int[] javaIntArray = {5, 4, 3, 2, 1};
        double[] javaDoubleArray = {5.2, 1.3};

        for (Object javaValue : Arrays.asList(javaByteArray, javaIntArray, javaDoubleArray)) {
            Any message = ProtobufInvocationHandler.packedMessage(javaValue);
            Object unpacked = MethodInvocationResolver.tryConvert(message, javaValue.getClass());
            assertArrayUnpackedCorrectly(javaValue, unpacked);
        }
    }

    private static void assertArrayUnpackedCorrectly(Object expected, Object actual) {
        assertNotNull("Actual array value is null", actual);
        if (expected instanceof byte[]) {
            assertThat(actual, instanceOf(byte[].class));
            assertArrayEquals((byte[]) expected, (byte[]) actual);
        } else if (expected instanceof int[]) {
            assertThat(actual, instanceOf(int[].class));
            assertArrayEquals((int[]) expected, (int[]) actual);
        } else if (expected instanceof double[]) {
            assertThat(actual, instanceOf(double[].class));
            assertArrayEquals((double[]) expected, (double[]) actual, 0.0);
        } else {
            throw new RuntimeException("Don't know how to verify " + expected);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private double avg(long[] numbers) {
        return LongStream.of(numbers).sorted().limit(numbers.length - 5).average().getAsDouble();
    }

}

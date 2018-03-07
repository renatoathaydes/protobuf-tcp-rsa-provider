package com.athaydes.protobuf.tcp.internal;

import com.athaydes.protobuf.tcp.internal.Internal.BooleanArray;
import com.athaydes.protobuf.tcp.internal.Internal.DoubleArray;
import com.athaydes.protobuf.tcp.internal.Internal.IntArray;
import com.athaydes.protobuf.tcp.internal.Internal.LongArray;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolver of method parameters given a method invocation arguments.
 */
@SuppressWarnings("UnnecessaryLocalVariable") // verify expressions have the right type
final class MethodInvocationResolver {

    /**
     * Type converter function.
     */
    private interface TypeConverter {
        Object apply(Any any) throws InvalidProtocolBufferException;
    }

    /**
     * Avoids having to check for both primitive and boxed types.
     */
    private static final Map<Class<?>, Class<?>> boxedTypes;

    /**
     * Functions converting Java types to protobuf value unpacking functions.
     */
    private static final Map<Class<?>, TypeConverter> typeConverters;

    static {
        Map<Class<?>, Class<?>> boxedTypes_ = new HashMap<>(8);
        boxedTypes_.put(boolean.class, Boolean.class);
        boxedTypes_.put(byte.class, Byte.class);
        boxedTypes_.put(short.class, Short.class);
        boxedTypes_.put(char.class, Character.class);
        boxedTypes_.put(int.class, Integer.class);
        boxedTypes_.put(long.class, Long.class);
        boxedTypes_.put(float.class, Float.class);
        boxedTypes_.put(double.class, Double.class);

        boxedTypes = Collections.unmodifiableMap(boxedTypes_);

        Map<Class<?>, TypeConverter> typeConverters_ = new HashMap<>(9);

        typeConverters_.put(String.class, any -> {
            String result = any.is(StringValue.class) ? any.unpack(StringValue.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Boolean.class, any -> {
            Boolean result = any.is(BoolValue.class) ? any.unpack(BoolValue.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Byte.class, any -> {
            ByteString bytes = any.is(BytesValue.class) ? any.unpack(BytesValue.class).getValue() : null;
            if (bytes == null || bytes.size() != 1) {
                return null;
            }
            return bytes.byteAt(0);
        });

        typeConverters_.put(Short.class, any -> {
            Short result = any.is(Int32Value.class) ? (short) any.unpack(Int32Value.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Character.class, any -> {
            String value = any.is(StringValue.class) ? any.unpack(StringValue.class).getValue() : null;
            if (value == null || value.length() != 1) {
                return null;
            }
            return value.charAt(0);
        });

        typeConverters_.put(Integer.class, any -> {
            Integer result = any.is(Int32Value.class) ? any.unpack(Int32Value.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Long.class, any -> {
            Long result = any.is(Int64Value.class) ? any.unpack(Int64Value.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Float.class, any -> {
            Float result = any.is(FloatValue.class) ? any.unpack(FloatValue.class).getValue() : null;
            return result;
        });

        typeConverters_.put(Double.class, any -> {
            Double result = any.is(DoubleValue.class) ? any.unpack(DoubleValue.class).getValue() : null;
            return result;
        });

        typeConverters_.put(byte[].class, any -> {
            List<Integer> list = any.is(IntArray.class) ? any.unpack(IntArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            byte[] result = new byte[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i).byteValue();
            }
            return result;
        });

        typeConverters_.put(boolean[].class, any -> {
            List<Boolean> list = any.is(BooleanArray.class) ? any.unpack(BooleanArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            boolean[] result = new boolean[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i);
            }
            return result;
        });

        typeConverters_.put(char[].class, any -> {
            String str = any.is(StringValue.class) ? any.unpack(StringValue.class).getValue() : null;
            if (str == null) {
                return null;
            }
            return str.toCharArray();
        });

        typeConverters_.put(short[].class, any -> {
            List<Integer> list = any.is(IntArray.class) ? any.unpack(IntArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            short[] result = new short[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i).shortValue();
            }
            return result;
        });

        typeConverters_.put(int[].class, any -> {
            List<Integer> list = any.is(IntArray.class) ? any.unpack(IntArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            int[] result = new int[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i);
            }
            return result;
        });

        typeConverters_.put(long[].class, any -> {
            List<Long> list = any.is(LongArray.class) ? any.unpack(LongArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            long[] result = new long[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i);
            }
            return result;
        });

        typeConverters_.put(double[].class, any -> {
            List<Double> list = any.is(DoubleArray.class) ? any.unpack(DoubleArray.class).getArrayList() : null;
            if (list == null) {
                return null;
            }
            double[] result = new double[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i);
            }
            return result;
        });

        typeConverters = Collections.unmodifiableMap(typeConverters_);
    }

    /**
     * Resolves the method invocation parameters given the provided arguments.
     *
     * @param method to being invoked
     * @param args   arguments for the method
     * @return a resolved method invocation if the arguments match the method parameters
     */
    static Optional<ResolvedInvocationInfo> resolveMethodInvocation(Method method, List<Any> args) {
        Class<?>[] parameterTypes = method.getParameterTypes();

        if (args.size() != parameterTypes.length) {
            return Optional.empty();
        }

        Object[] resolvedArgs = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = tryConvert(args.get(i), parameterTypes[i]);
            if (arg == null) {
                return Optional.empty();
            }
            resolvedArgs[i] = arg;
        }

        return Optional.of(new ResolvedInvocationInfo(method, resolvedArgs));
    }

    static Object convert(Any any, Class<?> type) throws InvalidProtocolBufferException {
        if (Message.class.isAssignableFrom(type)) {
            Class<? extends Message> messageType = type.asSubclass(Message.class);
            if (any.is(messageType)) {
                return any.unpack(messageType);
            }
        }

        // not a protobuf type, try a Java type
        return convertJavaType(any, type);
    }

    static Object tryConvert(Any any, Class<?> type) {
        try {
            return convert(any, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object convertJavaType(Any any, Class<?> type)
            throws InvalidProtocolBufferException {
        Class<?> boxedType = boxedTypes.getOrDefault(type, type);
        TypeConverter converter = typeConverters.get(boxedType);
        if (converter != null) {
            return converter.apply(any);
        } else {
            throw new IllegalArgumentException("Cannot convert " + type.getClass().getName() + " to protobuf message");
        }
    }

    static final class ResolvedInvocationInfo {
        private final Method method;
        private final Object[] parameters;

        private ResolvedInvocationInfo(Method method, Object[] parameters) {
            this.method = method;
            this.parameters = parameters;
        }

        Any callWith(Object object)
                throws InvocationTargetException, IllegalAccessException {
            Object result = method.invoke(object, parameters);
            Any message = ProtobufInvocationHandler.packedMessage(result);
            if (message == null) {
                if (method.getReturnType().equals(void.class)) {
                    return Any.getDefaultInstance();
                } else {
                    throw new NullPointerException("Remote service cannot return null value");
                }
            }
            return message;
        }

        @Override
        public String toString() {
            return "ResolvedInvocationInfo{" +
                    "method=" + method +
                    ", parameters=" + Arrays.toString(parameters) +
                    '}';
        }
    }

}

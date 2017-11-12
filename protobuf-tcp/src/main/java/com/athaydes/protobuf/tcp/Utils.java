package com.athaydes.protobuf.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A few stateless, helper functions.
 */
public final class Utils {

    private Utils() {
        // not instantiable
    }

    public static Optional<String> getStringFrom(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString);
    }

    public static OptionalInt getIntFrom(Map<String, Object> map, String key)
            throws NumberFormatException {
        Optional<Integer> optInt = Optional.ofNullable(map.get(key))
                .map(Object::toString)
                .map(Integer::parseInt);
        return optInt.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

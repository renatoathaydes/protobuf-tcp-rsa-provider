package com.athaydes.osgi.rsa.provider.protobuf;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A few stateless, helper functions.
 */
final class Utils {

    private Utils() {
        // not instantiable
    }

    static Optional<String> getStringFrom(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).map(Object::toString);
    }

    static OptionalInt getIntFrom(Map<String, Object> map, String key)
            throws NumberFormatException {
        Optional<Integer> optInt = Optional.ofNullable(map.get(key))
                .map(Object::toString)
                .map(Integer::parseInt);
        return optInt.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

package com.athaydes.protobuf.tcp.api;

import com.athaydes.protobuf.tcp.internal.Utils;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Helper for reading service properties from a {@link Map}.
 */
public interface ServicePropertyReader {

    /**
     * Read a String property from the given map.
     *
     * @param map with service properties
     * @param key the property key
     * @return the value of the property with the given key, or none if not available
     */
    Optional<String> getStringFrom(Map<String, Object> map, String key);

    /**
     * Read a {@code int} property from the given map.
     * <p>
     * If the property is present but is not an integer or a String that can be parsed as an integer, a
     * {@link NumberFormatException} exception is thrown.
     *
     * @param map with service properties
     * @param key the property key
     * @return the value of the property with the given key, or none if not available
     */
    OptionalInt getIntFrom(Map<String, Object> map, String key);

    /**
     * @return the default implementation of {@link ServicePropertyReader}.
     */
    static ServicePropertyReader getDefault() {
        return new Utils();
    }
}

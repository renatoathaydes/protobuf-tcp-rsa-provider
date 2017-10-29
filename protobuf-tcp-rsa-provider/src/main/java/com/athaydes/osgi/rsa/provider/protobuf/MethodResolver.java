package com.athaydes.osgi.rsa.provider.protobuf;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

/**
 * Resolver of valid methods clients can use from a service.
 */
final class MethodResolver {

    static Map<String, List<Method>> resolveMethods(Object service, Class[] exportedInterfaces) {
        if (exportedInterfaces.length == 0) {
            return resolveFromService(service);
        } else {
            return resolveFrom(exportedInterfaces);
        }
    }

    private static Map<String, List<Method>> resolveFrom(Class[] exportedInterfaces) {
        return Collections.unmodifiableMap(Arrays.stream(exportedInterfaces)
                .flatMap(exported -> Arrays.stream(exported.getMethods()))
                .collect(groupingBy(Method::getName)));
    }

    private static Map<String, List<Method>> resolveFromService(Object service) {
        return Arrays.stream(service.getClass().getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .collect(groupingBy(Method::getName));
    }
}

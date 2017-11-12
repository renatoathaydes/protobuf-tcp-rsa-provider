package com.athaydes.protobuf.tcp;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

        return methodsOfTypeHierarchy(service)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .collect(groupingBy(Method::getName));
    }

    private static Stream<Method> methodsOfTypeHierarchy(Object service) {
        Class<?> type = service.getClass();
        Stream<Method> methods = Stream.empty();
        while (!Object.class.equals(type)) {
            methods = Stream.concat(methods, Arrays.stream(type.getDeclaredMethods()));
            type = type.getSuperclass();
        }
        return methods;
    }
}

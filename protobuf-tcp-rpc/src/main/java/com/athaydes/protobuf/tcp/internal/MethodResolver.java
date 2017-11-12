package com.athaydes.protobuf.tcp.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/**
 * Resolver of valid methods clients can use from a service.
 */
final class MethodResolver {

    static Map<String, List<Method>> resolveMethods(Object service, Class[] exportedInterfaces) {
        if (exportedInterfaces.length == 0) {
            return resolveFromService(service);
        } else {
            validate(service, exportedInterfaces);
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

    private static void validate(Object service,
                                 Class[] exportedInterfaces) {
        Set<Class> implementedInterfaces = implementedInterfaces(service);
        for (Class exported : exportedInterfaces) {
            if (!implementedInterfaces.contains(exported)) {
                throw new ClassCastException(String.format("Cannot cast %s to %s",
                        service.getClass(), exported));
            }
        }
    }

    private static Set<Class> implementedInterfaces(Object service) {
        Set<Class> result = new HashSet<>(4);
        Class type = service.getClass();

        while (type != null && !Object.class.equals(type)) {
            Set<Class> interfaces = implementedInterfaces(type);
            result.addAll(interfaces);
            type = type.getSuperclass();
        }

        return result;
    }

    private static Set<Class> implementedInterfaces(Class interf) {
        return Stream.concat(Stream.of(interf),
                Stream.of(interf.getInterfaces())
                        .flatMap(it -> implementedInterfaces(it).stream()))
                .collect(toSet());
    }

}

package com.athaydes.osgi.rsa.provider.protobuf;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MethodResolverTest {

    @Test
    public void resolvesInterfaceMethodsService1() throws Exception {
        Map<String, List<Method>> methods = MethodResolver
                .resolveMethods(null, new Class[]{Service1.class});

        assertThat(methods.size(), equalTo(1));
        assertThat(methods.keySet(), equalTo(singleton("hello")));

        List<Method> helloMethods = methods.get("hello");

        assertThat(helloMethods.size(), equalTo(1));
        assertThat(helloMethods.get(0).getName(), equalTo("hello"));
        assertThat(helloMethods.get(0).getParameterTypes().length, equalTo(0));
    }

    @Test
    public void resolvesInterfaceMethodsService2() throws Exception {
        Map<String, List<Method>> methods = MethodResolver
                .resolveMethods(null, new Class[]{Service2.class});

        assertThat(methods.size(), equalTo(2));
        assertThat(methods.keySet(), equalTo(new HashSet<>(Arrays.asList("bye", "isCool"))));

        List<Method> byeMethods = methods.get("bye");
        List<Method> coolMethods = methods.get("isCool");

        assertThat(byeMethods.size(), equalTo(2));
        assertThat(byeMethods.get(0).getName(), equalTo("bye"));
        assertThat(byeMethods.get(1).getName(), equalTo("bye"));

        List<Class<?>[]> byeMethodsParams = byeMethods.stream()
                .map(Method::getParameterTypes)
                .collect(toList());

        Class<?>[] firstByeMethod = byeMethodsParams.get(0);
        Class<?>[] secondByeMethod = byeMethodsParams.get(1);

        // one of the overloads has 1 param, the other has 2
        assertTrue((firstByeMethod.length == 1 && secondByeMethod.length == 2) ||
                (firstByeMethod.length == 2 && secondByeMethod.length == 1));

        Class<?>[] singleParamOverload = firstByeMethod.length == 1 ? firstByeMethod : secondByeMethod;
        Class<?>[] twoParamOverload = firstByeMethod.length == 2 ? firstByeMethod : secondByeMethod;

        assertThat(singleParamOverload[0], equalTo(String.class));
        assertThat(twoParamOverload[0], equalTo(int.class));
        assertThat(twoParamOverload[1], equalTo(boolean.class));

        assertThat(coolMethods.size(), equalTo(1));
        assertThat(coolMethods.get(0).getName(), equalTo("isCool"));
        assertThat(coolMethods.get(0).getParameterTypes().length, equalTo(2));
        assertThat(coolMethods.get(0).getParameterTypes()[0], equalTo(String.class));
        assertThat(coolMethods.get(0).getParameterTypes()[1], equalTo(String.class));
    }

    @Test
    public void resolvesServicePublicMethods() throws Exception {
        Map<String, List<Method>> methods = MethodResolver
                .resolveMethods(new ServiceImpl(), new Class[]{});

        assertThat(methods.size(), equalTo(1));
        assertThat(methods.keySet(), equalTo(singleton("hello")));

        List<Method> helloMethods = methods.get("hello");

        assertThat(helloMethods.size(), equalTo(1));
        assertThat(helloMethods.get(0).getName(), equalTo("hello"));
        assertThat(helloMethods.get(0).getParameterTypes().length, equalTo(0));
    }

    interface Service1 {
        void hello();
    }

    interface Service2 {
        String bye(String name);

        String bye(int count, boolean ok);

        boolean isCool(String song, String author);
    }

    public static class Base {
        public void wow() {
        }

        protected String getText() {
            return "";
        }
    }

    public static class ServiceImpl extends Base {
        public void hello() {
        }

        private String hi() {
            return "";
        }

        protected boolean isHidden() {
            return true;
        }
    }
}

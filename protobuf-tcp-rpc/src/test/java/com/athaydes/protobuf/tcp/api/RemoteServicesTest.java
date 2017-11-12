package com.athaydes.protobuf.tcp.api;

import com.athaydes.protobuf.tcp.internal.Utils;
import java.io.Closeable;
import java.net.URI;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class RemoteServicesTest {

    public interface SimpleService {
        String hello(String name);
    }

    public interface OtherService {
        boolean mirror(boolean value);
    }

    public static class ImplementsTwoServices implements SimpleService, OtherService {
        @Override
        public String hello(String name) {
            return "Hello " + name;
        }

        @Override
        public boolean mirror(boolean value) {
            return value;
        }
    }

    private Closeable serviceToClose;

    @After
    public void closeService() {
        if (serviceToClose != null) {
            Utils.closeQuietly(serviceToClose);
        }
    }

    @Test
    public void canCastRemoteServiceClientToInterface() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing only SimpleService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the service the client implements
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // try to cast it to a service the server implements but not the client
        try {
            OtherService otherService = (OtherService) client;
            fail("Should not be able to call method on interface the client did not implement: " + otherService);
        } catch (ClassCastException e) {
            // expected
        }
    }

    @Test
    public void canCastRemoteServiceClientToSecondInterface() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing only OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the service the client implements
        OtherService service = (OtherService) client;

        // check if it works
        assertThat(service.mirror(true), equalTo(true));

        // try to cast it to a service the server implements but not the client
        try {
            SimpleService otherService = (SimpleService) client;
            fail("Should not be able to call method on interface the client did not implement: " + otherService);
        } catch (ClassCastException e) {
            // expected
        }
    }

    @Test
    public void canCastRemoteServiceClientToTwoInterfaces() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing SimpleService and OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class, OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the SimpleService
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // cast it to the OtherService
        OtherService otherService = (OtherService) client;

        // check if it works
        assertThat(otherService.mirror(true), equalTo(true));
    }

    @Test
    public void canCastRemoteServiceClientToInterfaceEvenIfServerDoesNotImplementIt() {
        // start the server providing only SimpleService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class);

        // create client implementing SimpleService and OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class, OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the SimpleService
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // cast it to a service the client implements but not the server
        OtherService otherService = (OtherService) client;

        // try to call a method on the client that the server does not provide
        try {
            otherService.mirror(false);
            fail("Should not be able to call method on interface the server did not provide");
        } catch (RemoteException e) {
            assertThat(e.getExceptionType(), equalTo(NoSuchMethodException.class.getName()));
        }
    }

}

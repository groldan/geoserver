/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.geoserver.catalog.Info;
import org.geoserver.config.ServiceInfo;
import org.geoserver.ows.util.ClassProperties;
import org.geoserver.ows.util.OwsUtils;
import org.geotools.util.logging.Logging;

public class EqualsAndHashCodeTestSupport {

    private static final Logger LOGGER = Logging.getLogger(EqualsAndHashCodeTestSupport.class);

    public void testEquals(Class<? extends Info> impl) {
        Info info1 = newInstance(impl);
        Info info2 = newInstance(impl);
        assertEquals("Unitialized values are not equal", info1, info2);
        testEquals(info1, info2);
    }

    public void testHashCode(Class<? extends Info> impl) {
        testHashCode(newInstance(impl));
    }

    private void testEquals(Info info1, Info info2) {
        assertNotSame(info1, info2);
        assertEquals(info1.getClass(), info2.getClass());

        final Class<? extends Info> abstractType = mostConcreteInterface(info1.getClass());
        ClassProperties props = OwsUtils.getClassProperties(abstractType);
        assertAllCollectionPropertiesAreInitialized(info1, abstractType, props);
        assertAllCollectionPropertiesAreInitialized(info2, abstractType, props);

    }

    private Class<? extends Info> mostConcreteInterface(Class<? extends Info> impl) {
        ClassMappings mappings = ClassMappings.fromImpl(impl);
        if (mappings != null) {
            Class<? extends Info> iface = mappings.getInterface();
            if (ServiceInfo.class.equals(iface)) {
                throw new UnsupportedOperationException("implement for concrete services");
            }
            return iface;
        }
        throw new UnsupportedOperationException("implement for " + impl.getName());
    }

    private void assertAllCollectionPropertiesAreInitialized(Info info,
            Class<? extends Info> abstractType, ClassProperties props) {

        Map<String, Class<?>> collectionProperties = collectionProperties(info.getClass(), props);
        collectionProperties.forEach((name, type) -> {
            Object value = OwsUtils.get(info, name);
            String msg = String.format("%s.%s of type %s is not initialized",
                    abstractType.getName(), name, type.getName());
            assertNotNull(msg, value);
        });
    }

    private Map<String, Class<?>> collectionProperties(Class<? extends Info> type,
            ClassProperties props) {

        Map<String, Class<?>> collectionProps = new HashMap<>();
        for (String name : props.properties()) {
            Method getter = props.getter(name, null);
            Class<?> returnType = getter.getReturnType();
            if (Collection.class.isAssignableFrom(returnType)
                    || Map.class.isAssignableFrom(returnType)) {
                collectionProps.put(name, returnType);
            }
        }
        return collectionProps;
    }


    private void testHashCode(Info info) {
        throw new UnsupportedOperationException();
    }


    public <T> T sampleProperty(Class<T> type, int index) {
        throw new UnsupportedOperationException();
    }

    private Info newInstance(Class<? extends Info> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

}

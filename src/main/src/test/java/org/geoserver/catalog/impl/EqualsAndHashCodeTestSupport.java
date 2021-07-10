/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.config.ServiceInfo;
import org.geoserver.ows.util.ClassProperties;
import org.geoserver.ows.util.OwsUtils;
import org.geotools.util.logging.Logging;

public class EqualsAndHashCodeTestSupport {

    private static final Logger LOGGER = Logging.getLogger(EqualsAndHashCodeTestSupport.class);

    /**
     * Properties to be ignored because they look like javabean properties but are not
     *
     * @see #mutableProperties(Class)
     */
    private static final Multimap<Class<?>, String> IGNORED_PROPERTIES =
            ImmutableSetMultimap.of(
                    // derived property, can't be set externally
                    WMSLayerInfo.class, "RemoteStyleInfos",
                    // derived property, can't be set externally
                    WMSLayerInfo.class, "Styles" //
                    );

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

        Map<String, Class<?>> properties = mutableProperties(abstractType, info1.getClass());
        Map<String, Class<?>> collectionProperties = collectionProperties(properties);
        Map<String, Class<?>> simpleProperties = simpleProperties(properties, collectionProperties);

        assertAllCollectionPropertiesAreInitialized(info1, abstractType, collectionProperties);
        assertAllCollectionPropertiesAreInitialized(info2, abstractType, collectionProperties);

        for (Map.Entry<String, Class<?>> e : simpleProperties.entrySet()) {
            testEqualsSimpleProperty(e.getKey(), e.getValue(), info1, info2);
        }

        for (Map.Entry<String, Class<?>> e : collectionProperties.entrySet()) {
            testEqualsCollectionProperty(e.getKey(), e.getValue(), info1, info2);
        }
    }

    private void testEqualsSimpleProperty(
            String propName, Class<?> propType, Info info1, Info info2) {

        assertEquals(info1, info2);
        Object value1 = sampleValue(propType);
        OwsUtils.set(info1, propName, value1);
        OwsUtils.set(info2, propName, value1);
        assertSame("Setter didn't change the value", value1, OwsUtils.get(info1, propName));
    }

    private Object sampleValue(Class<?> propType) {
        // TODO Auto-generated method stub
        return null;
    }

    private void testEqualsCollectionProperty(
            String propName, Class<?> propType, Info info1, Info info2) {
        Class<?> valueType = valueType(propType);
    }

    private Class<?> valueType(Class<?> collectionType) {
        // TODO Auto-generated method stub
        return null;
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

    private void assertAllCollectionPropertiesAreInitialized(
            Info info,
            Class<? extends Info> abstractType,
            Map<String, Class<?>> collectionProperties) {

        collectionProperties.forEach(
                (name, type) -> {
                    Object value = OwsUtils.get(info, name);
                    String msg =
                            String.format(
                                    "%s.%s of type %s is not initialized",
                                    abstractType.getName(), name, type.getName());
                    assertNotNull(msg, value);
                    assertTrue(value instanceof Collection || value instanceof Map);
                });
    }

    private Map<String, Class<?>> simpleProperties(
            Map<String, Class<?>> properties, Map<String, Class<?>> collectionProperties) {

        return Maps.difference(properties, collectionProperties).entriesOnlyOnLeft();
    }

    private Map<String, Class<?>> collectionProperties(Map<String, Class<?>> properties) {
        return properties
                .entrySet()
                .stream()
                .filter(
                        e ->
                                Collection.class.isAssignableFrom(e.getValue())
                                        || Map.class.isAssignableFrom(e.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private Map<String, Class<?>> mutableProperties(
            final Class<? extends Info> abstractType, Class<? extends Info> implType) {
        ClassProperties allProps = OwsUtils.getClassProperties(abstractType);

        Map<String, Class<?>> props = new HashMap<>();
        for (String name : allProps.properties()) {
            if (IGNORED_PROPERTIES.containsEntry(abstractType, name)) {
                LOGGER.info(
                        String.format(
                                "Ignoring %s.%s, not a real JavaBean property",
                                abstractType.getName(), name));
                continue;
            }
            Method getter = allProps.getter(name, null);
            if (0 == getter.getParameterCount()) {
                Class<?> returnType = getter.getReturnType();
                if (!isCollection(returnType)) {
                    Method setter = OwsUtils.setter(implType, name, returnType);
                    if (null == setter) {
                        LOGGER.info(
                                String.format(
                                        "Ignoring %s.%s, does not have setter in %s",
                                        abstractType.getName(), name, implType.getSimpleName()));
                        continue;
                    }
                }
                props.put(name, returnType);
            } else {
                LOGGER.info(
                        String.format(
                                "Ignoring %s.%s, not a JavaBean property",
                                abstractType.getName(), name));
            }
        }
        return props;
    }

    private boolean isCollection(Class<?> returnType) {
        return Collection.class.isAssignableFrom(returnType)
                || Map.class.isAssignableFrom(returnType);
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

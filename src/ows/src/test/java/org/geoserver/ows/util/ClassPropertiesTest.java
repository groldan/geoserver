/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.ows.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.geotools.util.logging.Logging;
import org.junit.Before;
import org.junit.Test;

public class ClassPropertiesTest {

    private ClassProperties root;
    private ClassProperties subtype;
    private ClassProperties rootImpl;
    private ClassProperties subtypeImpl;

    public @Before void before() {
        Logging.ALL.forceMonolineConsoleOutput();
        ClassProperties.LOGGER.setLevel(Level.FINEST);
        subtypeImpl = new ClassProperties(SubtypeImpl.class);
        rootImpl = new ClassProperties(RootImpl.class);
        subtype = new ClassProperties(Subtype.class);
        root = new ClassProperties(Root.class);
    }

    @Test
    public void testGetter() {
        testGetter(root, "stringProp", Root.class, CharSequence.class);
        testGetter(rootImpl, "stringProp", RootImpl.class, CharSequence.class);
        testGetter(subtype, "stringProp", Subtype.class, String.class);
        testGetter(subtypeImpl, "stringProp", RootImpl.class, CharSequence.class);

        testGetter(root, "NumberProp", Root.class, Number.class);
        testGetter(rootImpl, "NumberProp", RootImpl.class, Double.class);
        testGetter(subtype, "NumberProp", Subtype.class, Number.class);
        testGetter(subtypeImpl, "NumberProp", SubtypeImpl.class, Double.class);

        testGetter(root, "defaultProp", Root.class, Number.class);
        testGetter(rootImpl, "defaultProp", Root.class, Number.class);
        testGetter(subtype, "defaultProp", Root.class, Number.class);
        testGetter(subtypeImpl, "defaultProp", SubtypeImpl.class, Long.class);
    }

    void testGetter(ClassProperties cp, String propertyName, Class<?> expectedDeclaringClass,
            Class<?> expectedReturnType) {
        Method method = cp.getter(propertyName, null);
        assertMethod(method, expectedDeclaringClass, expectedReturnType);
    }

    private void assertMethod(Method method, Class<?> expectedDeclaringClass,
            Class<?> expectedReturnType) {
        assertNotNull(method);
        Class<?> declaringClass = method.getDeclaringClass();
        Class<?> returnType = method.getReturnType();

        String msg = String.format("expected %s.%s():%s, got %s.%s():%s",
                expectedDeclaringClass.getSimpleName(), method.getName(),
                expectedReturnType.getSimpleName(), declaringClass.getSimpleName(),
                method.getName(), returnType.getSimpleName());

        assertEquals("Return type mismatch: " + msg, expectedReturnType, returnType);
        assertEquals("Declaring type mismatch: " + msg, expectedDeclaringClass, declaringClass);
    }

    @Test
    public void testProperties() {}

    @Test
    public void testSetter() {
        fail("Not yet implemented");
    }


    @Test
    public void testMethod() {
        fail("Not yet implemented");
    }

    static interface Root<S extends CharSequence> {
        S getStringProp();

        void setStringProp(S value);

        <N extends Number> N getNumberProp();

        void setNumberProp(Number n);

        default Number getDefaultProp() {
            return 1;
        }
    }

    static interface Subtype extends Root<String> {
        @Override
        String getStringProp();

        @Override
        @SuppressWarnings("unchecked")
        Number getNumberProp();
    }

    static class RootImpl<S extends CharSequence> implements Root<S> {
        private S s;
        private Number n;

        @Override
        public S getStringProp() {
            return s;
        }

        @Override
        public void setStringProp(S value) {
            s = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Double getNumberProp() {
            return n == null ? null : n.doubleValue();
        }

        @Override
        public void setNumberProp(Number n) {
            this.n = n;
        }
    }

    static class SubtypeImpl extends RootImpl<String> implements Subtype {

        @Override
        @SuppressWarnings("unchecked")
        public Double getNumberProp() {
            return super.getNumberProp();
        }

        public @Override Long getDefaultProp() {
            return 2L;
        }
    }
}

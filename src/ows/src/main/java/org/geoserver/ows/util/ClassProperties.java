/*
 * (c) 2014 Open Source Geospatial Foundation - all rights reserved (c) 2001 - 2013 OpenPlans This
 * code is licensed under the GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.ows.util;

import static com.google.common.collect.Multimaps.newListMultimap;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geotools.util.logging.Logging;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Provides lookup information about java bean properties in a class.
 *
 * @author Justin Deoliveira, OpenGEO
 * @author Andrea Aime, OpenGEO
 */
public class ClassProperties {
    static final Logger LOGGER = Logging.getLogger(ClassProperties.class);
    
    private static final Set<String> COMMON_DERIVED_PROPERTIES =
            new HashSet<>(Arrays.asList("prefixedName"));
    final ListMultimap<String, Method> methods;
    final ListMultimap<String, Method> getters;
    final ListMultimap<String, Method> setters;

    public ClassProperties(Class<?> clazz) {
        LOGGER.warning(()->"Creating class properties of " + clazz.getSimpleName());

        ListMultimap<String, Method> methods = newCaseInsensitiveListMultimap();
        ListMultimap<String, Method> getters = newCaseInsensitiveListMultimap();
        ListMultimap<String, Method> setters = newCaseInsensitiveListMultimap();

        if(clazz.isInterface()) {
            addInterfacesMethods(methods, getters, setters, clazz);
        }else {
            addDeclaredMethods(methods, getters, setters, clazz);
            Class<?> superclass = clazz.getSuperclass();
            if(null != superclass && !Object.class.equals(superclass))
                addDeclaredMethods(methods, getters, setters, superclass);
            addInterfacesMethods(methods, getters, setters, clazz.getInterfaces());
            addDeclaredMethods(methods, getters, setters, Object.class);
        }
            
//        for(Class<?> iface : clazz.getInterfaces()) {
//            Class<?>[] interfaces = iface.getInterfaces();
//        }

        // for (Method method : clazz.getMethods()) {
        // methods.put(method.getName(), method);
        // if (isGetter(method)) {
        // getters.put(asPropertyName(method), method);
        // } else if (isSetter(method)) {
        // setters.put(asPropertyName(method), method);
        // }
        // }

        // avoid keeping lots of useless empty arrays in memory for
        // the long term, use just one
        this.methods = methods.isEmpty() ? ImmutableListMultimap.of() : methods;
        this.getters = getters.isEmpty() ? ImmutableListMultimap.of() : getters;
        this.setters = setters.isEmpty() ? ImmutableListMultimap.of() : setters;
        System.err.printf("%s getters: %n\t%s%n", clazz.getSimpleName(), getters.values().stream().map(
                m->String.format("%s.%s():%s [synthetic: %s, bridge: %s]",
                        m.getDeclaringClass().getSimpleName(), m.getName(),
                        m.getReturnType().getSimpleName(), m.isSynthetic(),
                        m.isBridge())).collect(Collectors.joining("\n\t")) );
    }

    private void addInterfacesMethods(ListMultimap<String, Method> methods,
            ListMultimap<String, Method> getters, ListMultimap<String, Method> setters,
            Class<?>... interfaces) {
        
        for (Class<?> iface : interfaces) {
            if(null==iface)continue;
            addDeclaredMethods(methods, getters, setters, iface);
            Class<?>[] superInterfaces = iface.getInterfaces();
            if (superInterfaces != null) {
                addInterfacesMethods(methods, getters, setters, superInterfaces);
            }
        }        
    }

    private void addDeclaredMethods(ListMultimap<String, Method> methods,
            ListMultimap<String, Method> getters, ListMultimap<String, Method> setters,
            Class<?>... classes) {

        for (Class<?> clazz : classes) {
            if(null==clazz)continue;
            Method[] declaredMethods = clazz.getDeclaredMethods();
            System.err.printf("Adding methods of %s:%n", clazz.getSimpleName());
            for (Method method : declaredMethods) {
                String signature = String.format("%s.%s():%s [synthetic: %s, bridge: %s]%n",
                        method.getDeclaringClass().getSimpleName(), method.getName(),
                        method.getReturnType().getSimpleName(), method.isSynthetic(),
                        method.isBridge());
                if(method.isSynthetic()) {
                    if (method.getName().startsWith("get") || method.getName().startsWith("set"))
                        System.err.printf("---" + signature);
                    continue;
                }else{
                    if (method.getName().startsWith("get") || method.getName().startsWith("set"))
                        System.err.printf("+++" + signature);
                }

                methods.put(method.getName(), method);
                if (isGetter(method)) {
                    getters.put(asPropertyName(method), method);
                } else if (isSetter(method)) {
                    setters.put(asPropertyName(method), method);
                }
            }
        }

    }

    private boolean isSetter(Method method) {
        return method.getName().startsWith("set") && 1 == method.getParameterCount();
    }

    private boolean isGetter(Method method) {
        String name = method.getName();
        int paramCount = method.getParameterCount();
        return 0 == paramCount && (name.startsWith("get") || name.startsWith("is")
                || COMMON_DERIVED_PROPERTIES.contains(name));
    }

    private ListMultimap<String, Method> newCaseInsensitiveListMultimap() {
        return newListMultimap(new TreeMap<>(CASE_INSENSITIVE_ORDER), ArrayList::new);
    }

    /**
     * Returns a list of all the properties of the class.
     *
     * @return A list of string.
     */
    public List<String> properties() {
        ArrayList<String> properties = new ArrayList<>();
        for (String key : getters.keySet()) {
            if (key.equals("Resource")) {
                properties.add(0, key);
            } else {
                properties.add(key);
            }
        }
        return properties;
    }

    /**
     * Looks up a setter method by property name.
     *
     * <p>
     * setter("foo",Integer) --&gt; void setFoo(Integer);
     *
     * @param property The property.
     * @param type The type of the property.
     * @return The setter for the property, or null if it does not exist.
     */
    public Method setter(String property, Class<?> type) {
        Collection<Method> methods = setters.get(property);
        for (Method setter : methods) {
            if (type == null) {
                return setter;
            } else {
                Class<?> target = setter.getParameterTypes()[0];
                if (target.isAssignableFrom(type)
                        || (target.isPrimitive() && type == wrapper(target))
                        || (type.isPrimitive() && target == wrapper(type))) {
                    return setter;
                }
            }
        }

        // could not be found, try again with a more lax match
        String lax = lax(property);
        if (!lax.equals(property)) {
            return setter(lax, type);
        }

        return null;
    }

    /**
     * Looks up a getter method by its property name.
     *
     * <p>
     * getter("foo",Integer) --&gt; Integer getFoo();
     *
     * @param property The property.
     * @param type The type of the property.
     * @return The getter for the property, or null if it does not exist.
     */
    public Method getter(String property, Class<?> type) {
        List<Method> methods = getters.get(property);
        // MultiMap never returns null
        for (Method getter : methods) {
            if (type == null) {
                return getter;
            } else {
                Class<?> target = getter.getReturnType();
                if (type.isAssignableFrom(target)
                        || (target.isPrimitive() && type == wrapper(target))
                        || (type.isPrimitive() && target == wrapper(type))) {
                    return getter;
                }
            }
        }

        // could not be found, try again with a more lax match
        String lax = lax(property);
        if (!lax.equals(property)) {
            return getter(lax, type);
        }

        return null;
    }

    /**
     * Does some checks on the property name to turn it into a java bean property.
     *
     * <p>
     * Checks include collapsing any "_" characters.
     */
    static String lax(String property) {
        return property.replaceAll("_", "");
    }

    /**
     * Returns the wrapper class for a primitive class.
     *
     * @param primitive A primtive class, like int.class, double.class, etc...
     */
    static Class<?> wrapper(Class<?> primitive) {
        if (boolean.class == primitive) {
            return Boolean.class;
        }
        if (char.class == primitive) {
            return Character.class;
        }
        if (byte.class == primitive) {
            return Byte.class;
        }
        if (short.class == primitive) {
            return Short.class;
        }
        if (int.class == primitive) {
            return Integer.class;
        }
        if (long.class == primitive) {
            return Long.class;
        }

        if (float.class == primitive) {
            return Float.class;
        }
        if (double.class == primitive) {
            return Double.class;
        }

        return null;
    }

    /** Looks up a method by name. */
    public Method method(String name) {
        List<Method> results = methods.get(name);
        if (results.isEmpty())
            return null;
        return results.get(0);
    }

    /** Returns the name of the property corresponding to the getter method. */
    String asPropertyName(Method getter) {
        String name = getter.getName();
        if (COMMON_DERIVED_PROPERTIES.contains(name)) {
            return name;
        }
        return name.substring(name.startsWith("get") || name.startsWith("set") ? 3 : 2);
    }
}

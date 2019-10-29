/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ows.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.AbstractIterator;

/**
 * Map decorator which makes String keys case-insensitive.
 *
 * @author Justin Deoliveira, The Open Planning Project, jdeolive@openplans.org
 */
public class CaseInsensitiveMap<K, V> implements Map<K, V> {

    private Map<CaseInsensitiveKey<K>, V> delegate;

    public CaseInsensitiveMap() {
        this.delegate = new HashMap<>();
    }

    public CaseInsensitiveMap(Map<K, V> delegate) {
        this();
        putAll(delegate);
    }

    public @Override void clear() {
        delegate.clear();
    }

    public @Override boolean containsKey(Object key) {
        return delegate.containsKey(upper(key));
    }

    public @Override boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    public @Override Set<Map.Entry<K, V>> entrySet() {
        return new CaseInsensitiveEntrySet();
    }

    @SuppressWarnings("rawtypes")
    public @Override boolean equals(Object o) {
        if (!(o instanceof Map)) {
            return false;
        }
        if (o instanceof CaseInsensitiveMap) {
            return delegate.equals(((CaseInsensitiveMap) o).delegate);
        }
        Map<?, ?> m = (Map<?, ?>) o;
        return entrySet().equals(m.entrySet());
    }

    public @Override V get(Object key) {
        return delegate.get(upper(key));
    }

    public @Override int hashCode() {
        return delegate.hashCode();
    }

    public @Override boolean isEmpty() {
        return delegate.isEmpty();
    }

    public @Override Set<K> keySet() {
        return new CaseInsensitiveKeySet();
    }

    public @Override V put(K key, V value) {
        return delegate.put(upper(key), value);
    }

    public @Override void putAll(Map<? extends K, ? extends V> t) {
        t.forEach((k, v) -> delegate.put(CaseInsensitiveKey.of(k), v));
    }

    public @Override V remove(Object key) {
        return delegate.remove(upper(key));
    }

    public @Override int size() {
        return delegate.size();
    }

    public @Override Collection<V> values() {
        return delegate.values();
    }

    @SuppressWarnings("unchecked")
    CaseInsensitiveKey<K> upper(Object key) {
        return (CaseInsensitiveKey<K>) CaseInsensitiveKey.of(key);
    }

    public @Override String toString() {
        return delegate.toString();
    }

    /**
     * Wraps a map in case insensitive one.
     *
     * <p>
     * If the instance is already a case insensitive map it is returned as is.
     */
    public static <K, V> Map<K, V> wrap(Map<K, V> other) {
        if (other instanceof CaseInsensitiveMap) {
            return other;
        }
        return new CaseInsensitiveMap<>(other);
    }

    private static final class CaseInsensitiveKey<K> {
        private K value;
        private int hash;// cache hashCode

        @SuppressWarnings("unchecked")
        public CaseInsensitiveKey(Object key) {
            this.value = (K) key;
            // compute the hash here at the constructor, it WILL be used
            int h = 0;
            if (value == null) {
                h = Integer.MIN_VALUE;
            } else {
                final String k = String.valueOf(value);
                final int len = k.length();
                for (int i = 0; i < len; i++) {
                    int c = Character.toUpperCase(k.codePointAt(i));
                    h = 31 * h + c;
                }
            }
            this.hash = h;
        }

        @SuppressWarnings("unchecked")
        public static <K> CaseInsensitiveKey<K> of(Object key) {
            return (key instanceof CaseInsensitiveKey) ? (CaseInsensitiveKey<K>) key : new CaseInsensitiveKey<>(key);
        }

        public K value() {
            return value;
        }

        public @Override boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof CaseInsensitiveKey)) {
                return false;
            }
            @SuppressWarnings("rawtypes")
            CaseInsensitiveKey<?> k = (CaseInsensitiveKey) o;
            if (hashCode() != k.hashCode()) {
                return false;
            }
            if (value == k.value || Objects.equals(value, k.value)) {
                return true;
            }
            K k1 = value();
            Object k2 = k.value();
            int c = String.valueOf(k1).compareToIgnoreCase(String.valueOf(k2));
            return c == 0;
        }

        public @Override int hashCode() {
            return this.hash;
        }

        public @Override String toString() {
            return String.format("%s[%s]", getClass().getSimpleName(), value);
        }
    }

    private final class CaseInsensitiveKeySet extends AbstractSet<K> {

        public @Override Iterator<K> iterator() {
            return new AbstractIterator<K>() {
                final Iterator<CaseInsensitiveKey<K>> delegate = CaseInsensitiveMap.this.delegate.keySet().iterator();

                protected @Override K computeNext() {
                    if (delegate.hasNext()) {
                        return delegate.next().value();
                    }
                    return endOfData();
                }
            };
        }

        public @Override final int size() {
            return CaseInsensitiveMap.this.size();
        }

        public @Override void clear() {
            CaseInsensitiveMap.this.clear();
        }

        public @Override boolean contains(Object o) {
            CaseInsensitiveKey<K> k = CaseInsensitiveKey.of(o);
            return CaseInsensitiveMap.this.containsKey(o);
        }

        public @Override boolean remove(Object key) {
            return null != CaseInsensitiveMap.this.remove(key);
        }

        public @Override void forEach(Consumer<? super K> action) {
            CaseInsensitiveMap.this.delegate.keySet().forEach(k -> action.accept(k.value()));
        }

        public @Override boolean equals(Object o) {
            if (!(o instanceof Set))
                return false;
            if (o == this)
                return true;
            Collection<?> c = (Collection<?>) o;
            if (c.size() != size())
                return false;
            try {
                return containsAll(c);
            } catch (ClassCastException unused) {
                return false;
            } catch (NullPointerException unused) {
                return false;
            }
        }

        public @Override int hashCode() {
            int h = CaseInsensitiveMap.this.delegate.keySet().stream().mapToInt(CaseInsensitiveKey::hashCode).sum();
            return h;
        }
    }

    private final class CaseInsensitiveEntrySet extends AbstractSet<Map.Entry<K, V>> {

        public @Override Iterator<Map.Entry<K, V>> iterator() {
            return new AbstractIterator<Map.Entry<K, V>>() {
                private final Iterator<Entry<CaseInsensitiveKey<K>, V>> subject = CaseInsensitiveMap.this.delegate
                        .entrySet().iterator();

                protected @Override Entry<K, V> computeNext() {
                    if (subject.hasNext()) {
                        Entry<CaseInsensitiveKey<K>, V> entry = subject.next();
                        return toOuterEntry(entry);
                    }
                    return endOfData();
                }
            };
        }

        private Entry<K, V> toOuterEntry(Entry<CaseInsensitiveKey<K>, V> entry) {
            return new AbstractMap.SimpleEntry<>(entry.getKey().value(), entry.getValue());
        }

        public @Override final int size() {
            return CaseInsensitiveMap.this.size();
        }

        public @Override final void clear() {
            CaseInsensitiveMap.this.clear();
        }

        public @Override boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Entry<?, ?>) o;
            CaseInsensitiveKey<?> key = CaseInsensitiveKey.of(entry.getKey());
            if (CaseInsensitiveMap.this.delegate.containsKey(key)) {
                V value = CaseInsensitiveMap.this.delegate.get(key);
                return Objects.equals(value, entry.getValue());
            }
            return false;
        }

        public @Override boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            return null != CaseInsensitiveMap.this.remove(entry.getKey());
        }

        public @Override void forEach(Consumer<? super Map.Entry<K, V>> action) {
            CaseInsensitiveMap.this.delegate.entrySet().forEach(e -> action.accept(toOuterEntry(e)));
        }
    }

}

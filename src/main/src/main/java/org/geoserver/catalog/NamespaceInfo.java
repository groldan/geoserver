/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.Objects;

/**
 * Application schema namespace.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface NamespaceInfo extends CatalogInfo {

    /**
     * The prefix of the namespace.
     *
     * <p>This prefix is unique among all namespace instances and can be used to identify a
     * particular namespace.
     *
     * @uml.property name="prefix"
     */
    String getPrefix();

    /**
     * Sets the prefix of the namespace.
     *
     * @uml.property name="prefix"
     */
    void setPrefix(String prefix);

    /** Returns the prefix of the namespace. */
    String getName();

    /**
     * The uri of the namespace.
     *
     * <p>This uri is unique among all namespace instances and can be used to identify a particular
     * namespace.
     *
     * @uml.property name="uRI"
     */
    String getURI();

    /**
     * Sets the uri of the namespace.
     *
     * @uml.property name="uRI"
     */
    void setURI(String uri);

    boolean isIsolated();

    void setIsolated(boolean isolated);

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@link NamespaceInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(NamespaceInfo o) {
        final int prime = 31;
        return prime * CatalogInfo.hashCode(o)
                + Objects.hash(o.isIsolated(), o.getPrefix(), o.getURI());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@link NamespaceInfo} and
     * another object based on the interface accessors
     *
     * @since 20.0
     */
    public static boolean equals(NamespaceInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof NamespaceInfo)) return false;
        NamespaceInfo other = (NamespaceInfo) obj;
        return CatalogInfo.equals(o, obj)
                && o.isIsolated() == other.isIsolated()
                && Objects.equals(o.getPrefix(), other.getPrefix())
                && Objects.equals(o.getURI(), other.getURI());
    }
}

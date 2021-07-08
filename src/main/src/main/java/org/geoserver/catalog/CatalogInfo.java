/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Base interface for all catalog objects.
 *
 * @author Justin Deoliveira, OpenGeo
 */
public interface CatalogInfo extends Info {

    public static final String TIME_CREATED = "creationTime";
    public static final String TIME_MODIFIED = "modificationTime";

    /** Accepts a visitor. */
    void accept(CatalogVisitor visitor);

    /** default implementation for returning date of modification */
    default Date getDateModified() {
        return null;
    }

    /** default implementation for returning date of creation */
    default Date getDateCreated() {
        return null;
    }

    public default void setDateCreated(Date dateCreated) {
        // do nothing
    }

    /** @param dateModified the dateModified to set */
    default void setDateModified(Date dateModified) {
        // do nothing
    }

    /**
     * A persistent map of metadata.
     *
     * <p>Data in this map is intended to be persisted. Common case of use is to have services
     * associate various bits of data with a particular namespace, style, etc.
     *
     * <p>The key values of this map are of type {@link String} and values are of type {@link
     * Serializable}.
     */
    default MetadataMap getMetadata() {
        return null;
    }

    default void setMetadata(MetadataMap metadata) {
        // do nothing
    }

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@link CatalogInfo} based on the
     * interface accessors
     */
    public static int hashCode(CatalogInfo o) {
        return Objects.hash(o.getDateCreated(), o.getDateModified(), o.getId(), o.getMetadata());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@link CatalogInfo} and
     * another object based on the interface accessors
     */
    public static boolean equals(CatalogInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof CatalogInfo)) return false;
        CatalogInfo other = (CatalogInfo) obj;
        return Objects.equals(o.getId(), other.getId())
                && Objects.equals(o.getDateCreated(), other.getDateCreated())
                && Objects.equals(o.getDateModified(), other.getDateModified())
                && Objects.equals(o.getMetadata(), other.getMetadata());
    }
}

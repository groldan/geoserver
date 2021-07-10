/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.Objects;

/**
 * A container of grouping for store objects.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface WorkspaceInfo extends CatalogInfo {

    /** The unique name of the workspace. */
    String getName();

    /** Sets the name of the workspace. */
    void setName(String name);

    boolean isIsolated();

    void setIsolated(boolean isolated);

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@link WorkspaceInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(WorkspaceInfo o) {
        final int prime = 31;
        return prime * CatalogInfo.hashCode(o) + Objects.hash(o.isIsolated(), o.getName());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@link WorkspaceInfo} and
     * another object based on the interface accessors
     *
     * @since 20.0
     */
    public static boolean equals(WorkspaceInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof WorkspaceInfo)) return false;
        WorkspaceInfo other = (WorkspaceInfo) obj;
        return CatalogInfo.equals(o, obj)
                && Objects.equals(o.getName(), other.getName())
                && o.isIsolated() == other.isIsolated();
    }
}

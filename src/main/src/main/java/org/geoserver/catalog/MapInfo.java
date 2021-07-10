/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.List;
import java.util.Objects;

/**
 * A grouping of layers.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface MapInfo extends CatalogInfo {

    /** The name of the map. */
    String getName();

    /** Sets the name of the map. */
    void setName(String name);

    /** Flag indicating if the map is enabled. */
    boolean isEnabled();

    /** Sets flag indicating if the map is enabled. */
    void setEnabled(boolean enabled);

    /** The layers that compose the map. */
    List<LayerInfo> getLayers();

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@link MapInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(MapInfo o) {
        final int prime = 31;
        return prime * CatalogInfo.hashCode(o)
                + Objects.hash(o.getName(), o.isEnabled(), o.getLayers());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@link MapInfo} and another
     * object based on the interface accessors
     *
     * @since 20.0
     */
    public static boolean equals(MapInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof MapInfo)) return false;
        MapInfo other = (MapInfo) obj;
        return CatalogInfo.equals(o, other)
                && o.isEnabled() == other.isEnabled()
                && Objects.equals(o.getName(), other.getName())
                && Objects.equals(o.getLayers(), other.getLayers());
    }
}

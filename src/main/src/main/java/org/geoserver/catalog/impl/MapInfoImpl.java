/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.List;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;

public class MapInfoImpl extends CatalogInfoImpl implements MapInfo {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean enabled;

    private List<LayerInfo> layers;

    public MapInfoImpl() {
        super();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<LayerInfo> getLayers() {
        return layers;
    }

    public void setLayers(List<LayerInfo> layers) {
        this.layers = layers;
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(name)
                .append(']')
                .toString();
    }

    @Override
    public int hashCode() {
        return MapInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return MapInfo.equals(this, obj);
    }
}

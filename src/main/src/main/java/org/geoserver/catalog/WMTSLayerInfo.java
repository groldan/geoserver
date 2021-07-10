/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.IOException;
import org.geotools.ows.wmts.model.WMTSLayer;
import org.opengis.util.ProgressListener;

public interface WMTSLayerInfo extends ResourceInfo {

    @Override
    public WMTSStoreInfo getStore();

    /** Returns the raw WMTS layer associated to this resource */
    public WMTSLayer getWMTSLayer(ProgressListener listener) throws IOException;

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code WMTSLayerInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(WMTSLayerInfo o) {
        final int prime = 31;
        return prime * ResourceInfo.hashCode(o);
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code WMTSLayerInfo} and
     * another object based on the interface accessors
     *
     * @since 20.0
     */
    public static boolean equals(WMTSLayerInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof WMTSLayerInfo)) return false;
        return ResourceInfo.equals(o, obj);
    }
}

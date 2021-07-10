/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.geotools.ows.wms.Layer;
import org.geotools.styling.Style;
import org.opengis.util.ProgressListener;

public interface WMSLayerInfo extends ResourceInfo {

    @Override
    public WMSStoreInfo getStore();

    /** Returns the raw WMS layer associated to this resource */
    public Layer getWMSLayer(ProgressListener listener) throws IOException;

    /** Return the DataURLs associated with this */
    public List<String> remoteStyles();

    public String getForcedRemoteStyle();

    public void setForcedRemoteStyle(String forcedRemoteStyle);

    public List<String> availableFormats();

    public Optional<Style> findRemoteStyleByName(final String name);

    public boolean isSelectedRemoteStyles(String name);

    public Set<StyleInfo> getRemoteStyleInfos();

    public List<String> getSelectedRemoteFormats();

    public void setSelectedRemoteFormats(List<String> selectedRemoteFormats);

    public List<String> getSelectedRemoteStyles();

    public void setSelectedRemoteStyles(List<String> selectedRemoteStyles);

    public boolean isFormatValid(String format);

    void reset();

    public String getPreferredFormat();

    public void setPreferredFormat(String prefferedFormat);

    public Set<StyleInfo> getStyles();

    public StyleInfo getDefaultStyle();

    public Double getMinScale();

    public void setMinScale(Double minScale);

    public Double getMaxScale();

    public void setMaxScale(Double maxScale);

    public boolean isMetadataBBoxRespected();

    public void setMetadataBBoxRespected(boolean metadataBBoxRespected);

    public List<StyleInfo> getAllAvailableRemoteStyles();

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code WMSLayerInfo} based on the
     * interface accessors
     */
    public static int hashCode(WMSLayerInfo o) {
        final int prime = 31;
        return prime * ResourceInfo.hashCode(o)
                + Objects.hash(
                        o.getAllAvailableRemoteStyles(),
                        o.getForcedRemoteStyle(),
                        o.getMaxScale(),
                        o.isMetadataBBoxRespected(),
                        o.getMinScale(),
                        o.getPreferredFormat(),
                        o.getSelectedRemoteFormats(),
                        o.getSelectedRemoteStyles());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code WMSLayerInfo} and
     * another object based on the interface accessors
     */
    public static boolean equals(WMSLayerInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof WMSLayerInfo)) return false;
        WMSLayerInfo other = (WMSLayerInfo) obj;
        return ResourceInfo.equals(o, other)
                && Objects.equals(
                        o.getAllAvailableRemoteStyles(), other.getAllAvailableRemoteStyles())
                && Objects.equals(o.getForcedRemoteStyle(), other.getForcedRemoteStyle())
                && Objects.equals(o.getMaxScale(), other.getMaxScale())
                && o.isMetadataBBoxRespected() == other.isMetadataBBoxRespected()
                && Objects.equals(o.getMinScale(), other.getMinScale())
                && Objects.equals(o.getPreferredFormat(), other.getPreferredFormat())
                && Objects.equals(o.getSelectedRemoteFormats(), other.getSelectedRemoteFormats())
                && Objects.equals(o.getSelectedRemoteStyles(), other.getSelectedRemoteStyles());
    }
}

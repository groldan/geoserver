/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.Objects;
import java.util.Set;

/**
 * A map layer.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface LayerInfo extends PublishedInfo {

    enum WMSInterpolation {
        Nearest,
        Bilinear,
        Bicubic
    }

    /** The rendering buffer */
    public static final String BUFFER = "buffer";

    /** Sets the type of the layer. */
    void setType(PublishedType type);

    /**
     * The path which this layer is mapped to.
     *
     * <p>This is the same path a client would use to address the layer.
     *
     * @uml.property name="path"
     */
    String getPath();

    /**
     * Sets the path this layer is mapped to.
     *
     * @uml.property name="path"
     */
    void setPath(String path);

    /**
     * The default style for the layer.
     *
     * @uml.property name="defaultStyle"
     * @uml.associationEnd inverse="resourceInfo:org.geoserver.catalog.StyleInfo"
     */
    StyleInfo getDefaultStyle();

    /**
     * Sets the default style for the layer.
     *
     * @uml.property name="defaultStyle"
     */
    void setDefaultStyle(StyleInfo defaultStyle);

    /**
     * The styles available for the layer.
     *
     * @uml.property name="styles"
     * @uml.associationEnd multiplicity="(0 -1)" container="java.util.Set"
     *     inverse="resourceInfo:org.geoserver.catalog.StyleInfo"
     */
    Set<StyleInfo> getStyles();

    /**
     * The resource referenced by this layer.
     *
     * @uml.property name="resource"
     * @uml.associationEnd inverse="layerInfo:org.geoserver.catalog.ResourceInfo"
     */
    ResourceInfo getResource();

    /**
     * Setter of the property <tt>resource</tt>
     *
     * @param resource The getResource to set.
     * @uml.property name="resource"
     */
    void setResource(ResourceInfo resource);

    /**
     * The legend for the layer.
     *
     * @uml.property name="legend"
     * @uml.associationEnd multiplicity="(0 -1)" inverse="legend:org.geoserver.catalog.LegendInfo"
     */
    LegendInfo getLegend();

    /**
     * Sets the legend for the layer.
     *
     * @uml.property name="legend"
     */
    void setLegend(LegendInfo legend);

    /**
     * Derived property indicating whether both this LayerInfo and its ResourceInfo are enabled.
     *
     * <p>Note this is a derived property and hence not part of the model. Consider it equal to
     * {@code getResource() != null && getResouce.enabled() && this.isEnabled()}
     *
     * @return the chained enabled status considering this object and it's associated ResourceInfo
     * @see #getResource()
     * @see ResourceInfo#enabled()
     */
    boolean enabled();

    /**
     * Sets the queryable status
     *
     * @param queryable {@code true} to set this Layer as queryable and subject of GetFeatureInfo
     *     requests, {@code false} to make the layer not queryable.
     */
    void setQueryable(boolean queryable);

    /**
     * Whether the layer is queryable and hence can be subject of a GetFeatureInfo request.
     *
     * <p>Defaults to {@code true}
     */
    boolean isQueryable();

    /**
     * Sets the opaque status
     *
     * @param opaque {@code true} to set this Layer as opaque, {@code false} to make the layer
     *     transparent.
     */
    void setOpaque(boolean opaque);

    /**
     * Controls layer transparency (whether the layer is opaque or transparent).
     *
     * <p>Defaults to {@code false}.
     *
     * @return Returns {@code true} for opaque layer, {@code false} for transparent.
     */
    boolean isOpaque();

    /**
     * The default WMS interpolation method.
     *
     * <p>If not specifed (i.e. {@code null}), the service default will be used.
     */
    WMSInterpolation getDefaultWMSInterpolationMethod();

    /**
     * Sets the default WMS interpolation method.
     *
     * <p>Admissible values are:
     *
     * <ul>
     *   <li><strong>Nearest</strong> - Nearest Neighbor Interpolation
     *   <li><strong>Bilinear</strong> - Bilinear Interpolation
     *   <li><strong>Bicubic</strong> - Bicubic Interpolation
     * </ul>
     *
     * @param interpolationMethod the interpolation method used by default
     */
    void setDefaultWMSInterpolationMethod(WMSInterpolation interpolationMethod);

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code LayerInfo} based on the
     * interface accessors
     *
     * @implNote the following properties are derived from {@link LayerInfo#getResource()} and hence
     *     not used directly within this method: {@link LayerInfo#getName() name}, {@link
     *     LayerInfo#isEnabled() enabled}, {@link LayerInfo#isAdvertised() advertised}, {@link
     *     LayerInfo#getTitle() title}, {@link LayerInfo#getAbstract() abstract}, {@link
     *     LayerInfo#getInternationalTitle() internationalTitle}, {@link
     *     LayerInfo#getInternationalAbstract() internationalAbstract},
     */
    public static int hashCode(LayerInfo o) {
        final int prime = 31;
        return prime * PublishedInfo.hashCode(o)
                + Objects.hash(
                        o.getDefaultStyle(),
                        o.getDefaultWMSInterpolationMethod(),
                        o.getLegend(),
                        o.isOpaque(),
                        o.getPath(),
                        o.isQueryable(),
                        o.getResource(),
                        o.getStyles(),
                        o.getType());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code LayerInfo} and another
     * object based on the interface accessors
     *
     * @implNote the following properties are derived from {@link LayerInfo#getResource()} and hence
     *     not used directly within this method: {@link LayerInfo#getName() name}, {@link
     *     LayerInfo#isEnabled() enabled}, {@link LayerInfo#isAdvertised() advertised}, {@link
     *     LayerInfo#getTitle() title}, {@link LayerInfo#getAbstract() abstract}, {@link
     *     LayerInfo#getInternationalTitle() internationalTitle}, {@link
     *     LayerInfo#getInternationalAbstract() internationalAbstract},
     */
    public static boolean equals(LayerInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof LayerInfo)) return false;
        LayerInfo other = (LayerInfo) obj;
        return PublishedInfo.equals(o, obj)
                && o.getType() == other.getType()
                && o.getDefaultWMSInterpolationMethod() == other.getDefaultWMSInterpolationMethod()
                && Objects.equals(o.getDefaultStyle(), other.getDefaultStyle())
                && Objects.equals(o.getLegend(), other.getLegend())
                && Objects.equals(o.isOpaque(), other.isOpaque())
                && Objects.equals(o.getPath(), other.getPath())
                && Objects.equals(o.isQueryable(), other.isQueryable())
                && Objects.equals(o.getStyles(), other.getStyles())
                && Objects.equals(o.getResource(), other.getResource());
    }
}

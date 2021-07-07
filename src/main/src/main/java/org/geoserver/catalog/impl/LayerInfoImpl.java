/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.AttributionInfo;
import org.geoserver.catalog.AuthorityURLInfo;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.LayerIdentifierInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.LegendInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.PublishedType;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geotools.util.GrowableInternationalString;
import org.geotools.util.logging.Logging;
import org.opengis.util.InternationalString;

public class LayerInfoImpl implements LayerInfo {

    static final Logger LOGGER = Logging.getLogger(LayerInfoImpl.class);

    static final String KEY_ADVERTISED = "advertised";

    protected String id;

    // this property has been left to ensure backwards compatibility with xstream but it's marked
    // transient
    // to avoid its value being serialized.
    // TODO: revert to normal property when the resource/publishing split is done
    protected transient String name;

    protected String path;

    protected PublishedType type;

    protected StyleInfo defaultStyle;

    protected Set<StyleInfo> styles = new HashSet<>();

    protected ResourceInfo resource;

    protected LegendInfo legend;

    // this property has been left to ensure backwards compatibility with xstream but it's marked
    // transient
    // to avoid its value being serialized.
    // TODO: revert to normal property when the resource/publishing split is done
    protected transient boolean enabled;

    // this property has been left to ensure backwards compatibility with xstream but it's marked
    // transient
    // to avoid its value being serialized.
    // TODO: revert to normal property when the resource/publishing split is done
    protected transient Boolean advertised;

    protected Boolean queryable;

    protected Boolean opaque;

    protected MetadataMap metadata = new MetadataMap();

    protected AttributionInfo attribution;

    /**
     * This property is transient in 2.1.x series and stored under the metadata map with key
     * "authorityURLs", and a not transient in the 2.2.x series.
     *
     * @since 2.1.3
     */
    protected List<AuthorityURLInfo> authorityURLs = new ArrayList<>(1);

    /**
     * This property is transient in 2.1.x series and stored under the metadata map with key
     * "identifiers", and a not transient in the 2.2.x series.
     *
     * @since 2.1.3
     */
    protected List<LayerIdentifierInfo> identifiers = new ArrayList<>(1);

    protected WMSInterpolation defaultWMSInterpolationMethod;

    protected Date dateCreated;

    protected Date dateModified;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        if (resource == null) {
            return name;
        }
        return resource.getName();
    }

    @Override
    public void setName(String name) {
        // TODO: remove this log and reinstate field assignment when resource/publish split is
        // complete
        LOGGER.log(
                Level.FINE,
                "Warning, some code is setting the LayerInfo name, but that will be ignored");
        this.name = name;

        if (resource == null) {
            throw new NullPointerException(
                    "Layer name must not be set without an underlying resource");
        }
        resource.setName(name);
    }

    @Override
    public String prefixedName() {
        return this.getResource().getStore().getWorkspace().getName() + ":" + getName();
    }

    @Override
    public PublishedType getType() {
        return type;
    }

    @Override
    public void setType(PublishedType type) {
        this.type = type;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public StyleInfo getDefaultStyle() {
        if (getResource() instanceof WMSLayerInfo) {
            StyleInfo remoteDefaultStyleInfo = ((WMSLayerInfo) getResource()).getDefaultStyle();
            // will be null if remote capability document
            // does not have any Style tags
            if (remoteDefaultStyleInfo != null) return remoteDefaultStyleInfo;
            else if (LOGGER.isLoggable(Level.FINE))
                LOGGER.fine(
                        "No Default Style found on cascaded WMS Resource"
                                + getResource().getName());
        }

        return defaultStyle;
    }

    @Override
    public void setDefaultStyle(StyleInfo defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    @Override
    public Set<StyleInfo> getStyles() {
        if (getResource() instanceof WMSLayerInfo) {
            Set<StyleInfo> remoteStyles = ((WMSLayerInfo) getResource()).getStyles();
            // will be null if remote capability document
            // does not have any Style tags
            if (remoteStyles != null) return remoteStyles;
            else if (LOGGER.isLoggable(Level.FINE))
                LOGGER.fine(
                        "No Default Styles found on cascaded WMS Resource"
                                + getResource().getName());
        }

        return styles;
    }

    public void setStyles(Set<StyleInfo> styles) {
        this.styles = styles;
    }

    @Override
    public ResourceInfo getResource() {
        return resource;
    }

    @Override
    public void setResource(ResourceInfo resource) {
        this.resource = resource;
    }

    @Override
    public LegendInfo getLegend() {
        return legend;
    }

    @Override
    public void setLegend(LegendInfo legend) {
        this.legend = legend;
    }

    @Override
    public AttributionInfo getAttribution() {
        return attribution;
    }

    @Override
    public void setAttribution(AttributionInfo attribution) {
        this.attribution = attribution;
    }

    @Override
    public boolean isEnabled() {
        if (resource == null) {
            throw new NullPointerException(
                    "Unable to get Layer enabled flag without an underlying resource");
        }
        return resource.isEnabled();
        // TODO: uncomment back when resource/publish split is complete
        // return name;
    }

    /** @see LayerInfo#enabled() */
    @Override
    public boolean enabled() {
        ResourceInfo resource = getResource();
        boolean resourceEnabled = resource != null && resource.enabled();
        boolean thisEnabled = this.isEnabled();
        return resourceEnabled && thisEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // TODO: remove this log and reinstate field assignment when resource/publish split is
        // complete
        LOGGER.log(
                Level.FINE,
                "Warning, some code is setting the LayerInfo enabled flag, but that will be ignored");
        this.enabled = enabled;

        if (resource == null) {
            throw new NullPointerException(
                    "Layer enabled flag must not be set without an underlying resource");
        }
        resource.setEnabled(enabled);
    }

    @Override
    public MetadataMap getMetadata() {
        checkMetadataNotNull();
        return metadata;
    }

    public void setMetadata(MetadataMap metadata) {
        this.metadata = metadata;
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                attribution,
                authorityURLs,
                dateCreated,
                dateModified,
                defaultStyle,
                defaultWMSInterpolationMethod,
                id,
                identifiers,
                legend,
                metadata,
                opaque,
                path,
                queryable,
                resource,
                styles,
                type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LayerInfo)) return false;
        LayerInfo other = (LayerInfo) obj;
        return Objects.equals(id, other.getId())
                && Objects.equals(identifiers, other.getIdentifiers())
                && type == other.getType()
                && Objects.equals(attribution, other.getAttribution())
                && Objects.equals(authorityURLs, other.getAuthorityURLs())
                && Objects.equals(dateCreated, other.getDateCreated())
                && Objects.equals(dateModified, other.getDateModified())
                && Objects.equals(defaultStyle, other.getDefaultStyle())
                && defaultWMSInterpolationMethod == other.getDefaultWMSInterpolationMethod()
                && Objects.equals(legend, other.getLegend())
                && Objects.equals(metadata, other.getMetadata())
                && Objects.equals(opaque, other.isOpaque())
                && Objects.equals(path, other.getPath())
                && Objects.equals(queryable, other.isQueryable())
                && Objects.equals(resource, other.getResource())
                && Objects.equals(styles, other.getStyles());
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(getName())
                .append(", resource:")
                .append(resource)
                .append(']')
                .toString();
    }

    @Override
    public void setQueryable(boolean queryable) {
        this.queryable = queryable;
    }

    @Override
    public boolean isQueryable() {
        return this.queryable == null ? true : this.queryable.booleanValue();
    }

    @Override
    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }

    @Override
    public boolean isOpaque() {
        return this.opaque == null ? false : this.opaque.booleanValue();
    }

    @Override
    public boolean isAdvertised() {
        if (resource == null) {
            throw new NullPointerException(
                    "Unable to get Layer advertised flag without an underlying resource");
        }
        return resource.isAdvertised();
        // TODO: uncomment back when resource/publish split is complete
        // return name;
    }

    @Override
    public void setAdvertised(boolean advertised) {
        // TODO: remove this log and reinstate field assignment when resource/publish split is
        // complete
        LOGGER.log(
                Level.FINE,
                "Warning, some code is setting the LayerInfo advertised flag, but that will be ignored");
        this.advertised = advertised;

        if (resource == null) {
            throw new NullPointerException(
                    "Layer advertised flag must not be set without an underlying resource");
        }
        resource.setAdvertised(advertised);
    }

    @Override
    public List<AuthorityURLInfo> getAuthorityURLs() {
        return authorityURLs;
    }

    public void setAuthorityURLs(List<AuthorityURLInfo> authorities) {
        this.authorityURLs = authorities;
    }

    @Override
    public List<LayerIdentifierInfo> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<LayerIdentifierInfo> identifiers) {
        this.identifiers = identifiers;
    }

    @Override
    public String getTitle() {
        return resource.getTitle();
    }

    @Override
    public void setTitle(String title) {
        this.resource.setTitle(title);
    }

    @Override
    public String getAbstract() {
        return this.resource.getAbstract();
    }

    @Override
    public void setAbstract(String abstractTxt) {
        this.resource.setAbstract(abstractTxt);
    }

    @Override
    public WMSInterpolation getDefaultWMSInterpolationMethod() {
        return defaultWMSInterpolationMethod;
    }

    @Override
    public void setDefaultWMSInterpolationMethod(WMSInterpolation interpolationMethod) {
        this.defaultWMSInterpolationMethod = interpolationMethod;
    }

    @Override
    public Date getDateModified() {
        return this.dateModified;
    }

    @Override
    public Date getDateCreated() {
        return this.dateCreated;
    }

    @Override
    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    @Override
    public void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }

    private void checkMetadataNotNull() {
        if (metadata == null) metadata = new MetadataMap();
    }

    @Override
    public GrowableInternationalString getInternationalTitle() {
        return (GrowableInternationalString) this.resource.getInternationalTitle();
    }

    @Override
    public void setInternationalTitle(InternationalString internationalTitle) {
        this.resource.setInternationalTitle(internationalTitle);
    }

    @Override
    public InternationalString getInternationalAbstract() {
        return this.resource.getInternationalAbstract();
    }

    @Override
    public void setInternationalAbstract(InternationalString internationalAbstract) {
        this.resource.setInternationalAbstract(internationalAbstract);
    }
}

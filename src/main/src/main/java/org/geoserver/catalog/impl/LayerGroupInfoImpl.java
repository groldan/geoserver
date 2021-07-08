/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.ArrayList;
import java.util.List;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.KeywordInfo;
import org.geoserver.catalog.LayerGroupHelper;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataLinkInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.PublishedType;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.util.GeoServerDefaultLocale;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.GrowableInternationalString;
import org.opengis.util.InternationalString;

public class LayerGroupInfoImpl extends PublishedInfoImpl implements LayerGroupInfo {
    private static final long serialVersionUID = 1L;

    protected String name;
    protected Mode mode = Mode.SINGLE;
    protected Boolean queryDisabled;

    /** This property in 2.2.x series is stored under the metadata map with key 'title'. */
    protected String title;

    /** This property in 2.2.x series is stored under the metadata map with key 'abstract'. */
    protected String abstractTxt;

    protected Boolean enabled;

    protected Boolean advertised;

    protected WorkspaceInfo workspace;
    /*
     * REVISIT: there's not 'path' property in LayerGroupInfo, is this a remnant from older versions?
     */
    protected transient String path;
    protected LayerInfo rootLayer;
    protected StyleInfo rootLayerStyle;

    protected GrowableInternationalString internationalTitle;

    protected GrowableInternationalString internationalAbstract;

    /**
     * This property is here for compatibility purpose, in 2.3.x series it has been replaced by
     * 'publishables'
     */
    protected List<LayerInfo> layers;

    protected List<PublishedInfo> publishables;
    protected List<StyleInfo> styles;
    protected List<MetadataLinkInfo> metadataLinks;

    protected ReferencedEnvelope bounds;

    private List<KeywordInfo> keywords;

    @Override
    protected Object readResolve() {
        super.readResolve();
        if (null == mode) {
            mode = Mode.SINGLE;
        }
        return this;
    }

    @Override
    public List<KeywordInfo> getKeywords() {
        return keywords;
    }

    /**
     * Set the keywords of this layer group. The provided keywords will override any existing
     * keywords no merge will be done.
     *
     * @param keywords new keywords of this layer group
     */
    public void setKeywords(List<KeywordInfo> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
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
    public Mode getMode() {
        return mode;
    }

    @Override
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public boolean isEnabled() {
        if (this.enabled != null) return this.enabled;
        else return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isQueryDisabled() {
        return queryDisabled != null ? queryDisabled.booleanValue() : false;
    }

    @Override
    public void setQueryDisabled(boolean queryDisabled) {
        this.queryDisabled = queryDisabled ? Boolean.TRUE : null;
    }

    @Override
    public String getTitle() {
        if (title == null && internationalTitle != null)
            return internationalTitle.toString(GeoServerDefaultLocale.get());
        if (title == null) {
            title = getMetadata().get("title", String.class);
        }
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean isAdvertised() {
        if (this.advertised != null) {
            return advertised;
        } else {
            return true;
        }
    }

    @Override
    public void setAdvertised(boolean advertised) {
        this.advertised = advertised;
    }

    @Override
    public String getAbstract() {
        if (abstractTxt == null && internationalAbstract != null)
            return internationalAbstract.toString(GeoServerDefaultLocale.get());
        if (abstractTxt == null) {
            abstractTxt = getMetadata().get("title", String.class);
        }
        return abstractTxt;
    }

    @Override
    public void setAbstract(String abstractTxt) {
        this.abstractTxt = abstractTxt;
    }

    @Override
    public WorkspaceInfo getWorkspace() {
        return workspace;
    }

    @Override
    public void setWorkspace(WorkspaceInfo workspace) {
        this.workspace = workspace;
    }

    @Override
    public String prefixedName() {
        return workspace != null ? workspace.getName() + ":" + name : name;
    }

    @Override
    public LayerInfo getRootLayer() {
        return rootLayer;
    }

    @Override
    public void setRootLayer(LayerInfo rootLayer) {
        this.rootLayer = rootLayer;
    }

    @Override
    public StyleInfo getRootLayerStyle() {
        return rootLayerStyle;
    }

    @Override
    public void setRootLayerStyle(StyleInfo style) {
        this.rootLayerStyle = style;
    }

    @Override
    public List<PublishedInfo> getLayers() {
        return publishables;
    }

    public void setLayers(List<PublishedInfo> publishables) {
        this.publishables = publishables;
    }

    /**
     * Used after deserialization. It converts 'layers' property content, used until 2.3.x, to
     * 'publishables' property content.
     */
    public void convertLegacyLayers() {
        if (layers != null && publishables == null) {
            publishables = new ArrayList<>();
            for (LayerInfo layer : layers) {
                publishables.add(layer);
            }
            layers = null;
        }
    }

    @Override
    public List<StyleInfo> getStyles() {
        return styles;
    }

    public void setStyles(List<StyleInfo> styles) {
        this.styles = styles;
    }

    @Override
    public List<LayerInfo> layers() {
        LayerGroupHelper helper = new LayerGroupHelper(this);
        return helper.allLayersForRendering();
    }

    @Override
    public List<StyleInfo> styles() {
        LayerGroupHelper helper = new LayerGroupHelper(this);
        return helper.allStylesForRendering();
    }

    @Override
    public ReferencedEnvelope getBounds() {
        return bounds;
    }

    @Override
    public void setBounds(ReferencedEnvelope bounds) {
        this.bounds = bounds;
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return LayerGroupInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return LayerGroupInfo.equals(this, obj);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(name)
                .append(']')
                .toString();
    }

    public String getPrefixedName() {
        return prefixedName();
    }

    @Override
    public PublishedType getType() {
        return PublishedType.GROUP;
    }

    @Override
    public List<MetadataLinkInfo> getMetadataLinks() {
        return metadataLinks;
    }

    public void setMetadataLinks(List<MetadataLinkInfo> metadataLinks) {
        this.metadataLinks = metadataLinks;
    }

    @Override
    public GrowableInternationalString getInternationalTitle() {
        if (this.internationalTitle == null || this.internationalTitle.toString().equals("")) {
            return new GrowableInternationalString(getTitle());
        }
        return this.internationalTitle;
    }

    @Override
    public void setInternationalTitle(InternationalString internationalTitle) {
        GrowableInternationalString growable;
        if (internationalTitle == null) growable = new GrowableInternationalString(getTitle());
        else growable = new GrowableInternationalString(internationalTitle);

        this.internationalTitle = growable;
    }

    @Override
    public GrowableInternationalString getInternationalAbstract() {
        if (this.internationalAbstract == null || this.internationalAbstract.toString().equals(""))
            return new GrowableInternationalString(getAbstract());
        return this.internationalAbstract;
    }

    @Override
    public void setInternationalAbstract(InternationalString internationalAbstract) {
        GrowableInternationalString growable;
        if (internationalAbstract == null)
            growable = new GrowableInternationalString(getAbstract());
        else growable = new GrowableInternationalString(internationalAbstract);

        this.internationalAbstract = growable;
    }
}

/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.LegendInfo;
import org.geoserver.catalog.SLDHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.styling.Style;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.util.Version;

public class StyleInfoImpl extends CatalogInfoImpl implements StyleInfo {
    private static final long serialVersionUID = 1L;

    /** Marks a remote style, generated on the fly from a capabilites document */
    public static final String IS_REMOTE = "isRemote";

    protected String name;

    protected WorkspaceInfo workspace;

    // not used, maininting this property for xstream backward compatability
    protected transient Version sldVersion;

    protected String format = SLDHandler.FORMAT;

    protected Version languageVersion = SLDHandler.VERSION_10;

    protected String filename;

    protected LegendInfo legend;

    protected transient Catalog catalog;

    protected StyleInfoImpl() {}

    public StyleInfoImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    public void setCatalog(Catalog catalog) {
        this.catalog = catalog;
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
    public WorkspaceInfo getWorkspace() {
        return workspace;
    }

    @Override
    public void setWorkspace(WorkspaceInfo workspace) {
        this.workspace = workspace;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public void setFormat(String language) {
        this.format = language;
    };

    @Override
    public Version getFormatVersion() {
        return languageVersion;
    }

    @Override
    public void setFormatVersion(Version version) {
        this.languageVersion = version;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Override
    public Style getStyle() throws IOException {
        // for capability document request
        // remote style does not exist in local catalog
        // do not look for this style inside ResourcePool
        if (getMetadata() != null)
            if (getMetadata().containsKey(IS_REMOTE)) return WMSLayerInfoImpl.getStyleInfo(this);
        return catalog.getResourcePool().getStyle(this);
    }

    @Override
    public StyledLayerDescriptor getSLD() throws IOException {
        return catalog.getResourcePool().getSld(this);
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
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return StyleInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return StyleInfo.equals(this, obj);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(prefixedName())
                .append(']')
                .toString();
    }

    @Override
    public String prefixedName() {
        if (workspace != null) {
            return workspace.getName() + ":" + getName();
        } else {
            return getName();
        }
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        // this check is here to enable smooth migration from old configurations that don't have
        // the version property, and a transition from the deprecated sldVersion property

        if (format == null) {
            format = SLDHandler.FORMAT;
        }

        if (languageVersion == null && sldVersion != null) {
            languageVersion = sldVersion;
        }
        if (languageVersion == null) {
            languageVersion = SLDHandler.VERSION_10;
        }

        return this;
    }
}

/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.WorkspaceInfo;

public class WorkspaceInfoImpl implements WorkspaceInfo, Serializable {

    protected String id;
    protected String name;
    protected boolean _default;

    protected MetadataMap metadata = new MetadataMap();

    private boolean isolated = false;

    protected Date dateCreated;

    protected Date dateModified;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isDefault() {
        return _default;
    }

    public void setDefault(boolean _default) {
        this._default = _default;
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
    public MetadataMap getMetadata() {
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
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        return Objects.hash(dateCreated, dateModified, id, isolated, metadata, name);
    }

    @Override
    public boolean equals(Object obj) {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        if (this == obj) return true;
        if (!(obj instanceof WorkspaceInfo)) return false;
        WorkspaceInfo other = (WorkspaceInfo) obj;
        return Objects.equals(id, other.getId())
                && Objects.equals(name, other.getName())
                && Objects.equals(dateCreated, other.getDateCreated())
                && Objects.equals(dateModified, other.getDateModified())
                && isolated == other.isIsolated()
                && Objects.equals(metadata, other.getMetadata());
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
    public boolean isIsolated() {
        return isolated;
    }

    @Override
    public void setIsolated(boolean isolated) {
        this.isolated = isolated;
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
}

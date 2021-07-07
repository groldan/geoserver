/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Objects;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.NamespaceInfo;

public class NamespaceInfoImpl implements NamespaceInfo {

    protected String id;

    protected String prefix;

    protected String uri;

    protected boolean _default;

    protected MetadataMap metadata = new MetadataMap();

    private boolean isolated;

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
    public String getPrefix() {
        return prefix;
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return getPrefix();
    }

    @Override
    public String getURI() {
        return uri;
    }

    @Override
    public void setURI(String uri) {
        this.uri = uri;
    }

    @Override
    public MetadataMap getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataMap metadata) {
        this.metadata = metadata;
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
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(prefix)
                .append(':')
                .append(uri)
                .append(']')
                .toString();
    }

    @Override
    public int hashCode() {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        return Objects.hash(id, isolated, metadata, prefix, uri);
    }

    @Override
    public boolean equals(Object obj) {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        if (this == obj) return true;
        if (!(obj instanceof NamespaceInfo)) return false;
        NamespaceInfo other = (NamespaceInfo) obj;
        return Objects.equals(id, other.getId())
                && isolated == other.isIsolated()
                && Objects.equals(prefix, other.getPrefix())
                && Objects.equals(uri, other.getURI())
                && Objects.equals(metadata, other.getMetadata());
    }
}

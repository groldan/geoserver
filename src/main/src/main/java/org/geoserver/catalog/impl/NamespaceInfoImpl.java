/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.NamespaceInfo;

public class NamespaceInfoImpl extends CatalogInfoImpl implements NamespaceInfo {
    private static final long serialVersionUID = 1L;

    protected String prefix;

    protected String uri;

    protected boolean _default;

    private boolean isolated;

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
        return NamespaceInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        return NamespaceInfo.equals(this, obj);
    }
}

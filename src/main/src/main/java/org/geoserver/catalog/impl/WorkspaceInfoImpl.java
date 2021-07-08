/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.WorkspaceInfo;

public class WorkspaceInfoImpl extends CatalogInfoImpl implements WorkspaceInfo {
    private static final long serialVersionUID = 1L;

    protected String name;
    protected boolean _default;

    private boolean isolated = false;

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
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        return WorkspaceInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        // Note _default is to be ignored, not part of NamespaceInfo, but merely an implementation
        // detail for the default (data directory) catalog persistence
        return WorkspaceInfo.equals(this, obj);
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
}

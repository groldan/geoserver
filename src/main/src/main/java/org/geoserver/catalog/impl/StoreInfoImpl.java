/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.opengis.util.ProgressListener;

/** Default implementation of {@link StoreInfo}. */
@SuppressWarnings("serial")
public abstract class StoreInfoImpl extends CatalogInfoImpl implements StoreInfo {

    protected String name;

    protected String description;

    protected String type;

    protected boolean enabled;

    protected WorkspaceInfo workspace;

    protected transient Catalog catalog;

    protected Map<String, Serializable> connectionParameters;

    // TODO: REMOVE, this is dead-code. Set by GeoServerLoader, but the accessor is not used
    // throughout the entire codebase
    protected transient Throwable error;

    protected boolean _default;

    protected StoreInfoImpl() {}

    protected StoreInfoImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    protected StoreInfoImpl(Catalog catalog, String id) {
        this(catalog);
        setId(id);
    }

    @Override
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
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    public Map<String, Serializable> getConnectionParameters() {
        return connectionParameters;
    }

    public void setConnectionParameters(Map<String, Serializable> connectionParameters) {
        this.connectionParameters = connectionParameters;
    }

    @Override
    public <T extends Object> T getAdapter(Class<T> adapterClass, Map<?, ?> hints) {
        // subclasses should override
        return null;
    }

    public Iterator<?> getResources(ProgressListener monitor) throws IOException {
        // subclasses should override
        return null;
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
    public Throwable getError() {
        return error;
    }

    @Override
    public void setError(Throwable error) {
        this.error = error;
    }

    public boolean isDefault() {
        return _default;
    }

    public void setDefault(boolean _default) {
        this._default = _default;
    }

    @Override
    public int hashCode() {
        return StoreInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return StoreInfo.equals(this, obj);
    }
}

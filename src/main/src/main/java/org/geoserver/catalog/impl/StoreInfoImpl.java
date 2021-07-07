/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.util.Converters;
import org.opengis.util.ProgressListener;

/** Default implementation of {@link StoreInfo}. */
@SuppressWarnings("serial")
public abstract class StoreInfoImpl implements StoreInfo {

    protected String id;

    protected String name;

    protected String description;

    protected String type;

    protected boolean enabled;

    protected WorkspaceInfo workspace;

    protected transient Catalog catalog;

    protected Map<String, Serializable> connectionParameters = new HashMap<>();

    protected MetadataMap metadata = new MetadataMap();

    // TODO: REMOVE, this is dead-code. Set by GeoServerLoader, but the accessor is not used
    // throughout the entire codebase
    protected transient Throwable error;

    protected boolean _default;

    protected Date dateCreated;

    protected Date dateModified;

    protected StoreInfoImpl() {}

    protected StoreInfoImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    protected StoreInfoImpl(Catalog catalog, String id) {
        this(catalog);
        setId(id);
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
    public synchronized MetadataMap getMetadata() {
        if (metadata == null) {
            metadata = new MetadataMap();
        }
        return metadata;
    }

    public void setMetadata(MetadataMap metadata) {
        this.metadata = metadata;
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
        return Objects.hash(
                connectionParameters,
                dateCreated,
                dateModified,
                description,
                enabled,
                id,
                metadata,
                name,
                type,
                workspace == null ? null : workspace.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StoreInfo)) return false;
        StoreInfo other = (StoreInfo) obj;
        return Objects.equals(id, other.getId())
                && Objects.equals(workspace, other.getWorkspace())
                && Objects.equals(dateCreated, other.getDateCreated())
                && Objects.equals(dateModified, other.getDateModified())
                && Objects.equals(description, other.getDescription())
                && enabled == other.isEnabled()
                && Objects.equals(metadata, other.getMetadata())
                && Objects.equals(name, other.getName())
                && Objects.equals(type, other.getType())
                && connectionParametersAreEqual(
                        connectionParameters, other.getConnectionParameters());
    }

    /**
     * Accounts for the fact that parameter values might be equivalent though of different types,
     * and {@link Param#lookUp(Map) DataAccessFactory$Param#lookUp(Map)} will perform the type
     * conversion. E.g., a {@code boolean} parameter might have a {@code Boolean} or {@code String}
     * value in the map.
     */
    private boolean connectionParametersAreEqual(Map<String, ?> params1, Map<String, ?> params2) {
        if (params1 == params2) return true;
        if (params1 == null || params2 == null) return false;
        if (!params1.keySet().equals(params2.keySet())) return false;
        for (Map.Entry<String, ?> e1 : params1.entrySet()) {
            Object v1 = e1.getValue();
            Object v2 = params2.get(e1.getKey());
            if (!Objects.equals(v1, v2)) {
                String s1 = Converters.convert(v1, String.class);
                String s2 = Converters.convert(v2, String.class);
                if ((v1 != null && s1 == null)
                        || (v2 != null && s2 == null)
                        || !Objects.equals(s1, s2)) {
                    return false;
                }
            }
        }
        return true;
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

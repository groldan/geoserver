/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Date;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.ows.util.OwsUtils;

abstract class CatalogInfoImpl implements CatalogInfo {
    private static final long serialVersionUID = 1L;

    private String id;
    private Date dateCreated;
    private Date dateModified;
    private MetadataMap metadata;

    public CatalogInfoImpl() {
        readResolve();
    }

    /**
     * Initializes collection members. Especially useful when the no-args constructor is not called
     * by reflection code to ensure consistency of equals() and hashCode()
     *
     * @return {@code this}
     */
    protected Object readResolve() {
        OwsUtils.resolveCollections(this);
        if (null == metadata) {
            metadata = new MetadataMap();
        }
        return this;
    }

    @Override
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
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

    /** @return non {@code null} metadata map */
    @Override
    public MetadataMap getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(MetadataMap metadata) {
        if (metadata == null) this.metadata = new MetadataMap();
        else this.metadata = metadata;
    }

    @Override
    public int hashCode() {
        return CatalogInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return CatalogInfo.equals(this, obj);
    }
}

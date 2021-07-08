/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Date;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.ows.util.OwsUtils;

/**
 * @implNote In order to ensure consistent comparisons at {@link #equals(Object)} and consistent
 *     results for {@link #hashCode()}, all collection properties are guaranteed to be non-null once
 *     an instance of a concrete subtype is returned to the caller, through an implicit (this class'
 *     default constructor) or explicit (unmarshaller) call to {@link #readResolve()}, which will
 *     call {@link OwsUtils#resolveCollections OwsUtils.resolveCollections(this)}. Note, however,
 *     that the time {@link #readResolve()} is called will differ depending on whether the instance
 *     is being created through a constructor (e.g. {@code new} statement or constructor based
 *     reflection), or bypassing constructors, like in the case of XStream deserialization, which
 *     instantiates through {@code java.misc.Unsafe.allocateInstance(Class)}, and calls {@link
 *     #readResolve()} after unmarshalling. That means, only in the case that a constructor needs
 *     access to a collection property to perform some initialization logic, it must be aware that
 *     the collection property might still be {@code null}. Otherwise, collection property
 *     declarations can skip initialization, for the sake of coding style consistency.
 */
abstract class CatalogInfoImpl implements CatalogInfo {
    private static final long serialVersionUID = 1L;

    private String id;
    private Date dateCreated;
    private Date dateModified;
    private MetadataMap metadata = new MetadataMap();

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

    /** @return the metadata map, possibly empty, never {@code null} */
    @Override
    public MetadataMap getMetadata() {
        if (null == metadata) {
            metadata = new MetadataMap();
        }
        return metadata;
    }

    /**
     * Set the internal {@link MetadataMap} to the new value, or to a new empty instance if {@code
     * null} is given
     */
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

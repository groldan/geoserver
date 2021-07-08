/* (c) 2021 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.List;
import org.geoserver.catalog.AttributionInfo;
import org.geoserver.catalog.AuthorityURLInfo;
import org.geoserver.catalog.LayerIdentifierInfo;
import org.geoserver.catalog.PublishedInfo;

abstract class PublishedInfoImpl extends CatalogInfoImpl implements PublishedInfo {
    private static final long serialVersionUID = 1L;

    private AttributionInfo attribution;

    /**
     * This property is transient in 2.1.x series and stored under the metadata map with key
     * "authorityURLs", and a not transient in the 2.2.x series.
     *
     * @since 2.1.3
     */
    private List<AuthorityURLInfo> authorityURLs;

    /**
     * This property is transient in 2.1.x series and stored under the metadata map with key
     * "identifiers", and a not transient in the 2.2.x series.
     *
     * @since 2.1.3
     */
    private List<LayerIdentifierInfo> identifiers;

    @Override
    public List<AuthorityURLInfo> getAuthorityURLs() {
        return authorityURLs;
    }

    public void setAuthorityURLs(List<AuthorityURLInfo> authorities) {
        this.authorityURLs = authorities;
    }

    @Override
    public List<LayerIdentifierInfo> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<LayerIdentifierInfo> identifiers) {
        this.identifiers = identifiers;
    }

    @Override
    public AttributionInfo getAttribution() {
        return attribution;
    }

    @Override
    public void setAttribution(AttributionInfo attribution) {
        this.attribution = attribution;
    }

    @Override
    public int hashCode() {
        return PublishedInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return PublishedInfo.equals(this, obj);
    }
}

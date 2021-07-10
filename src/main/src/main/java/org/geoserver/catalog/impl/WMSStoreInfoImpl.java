/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.WMSStoreInfo;
import org.geotools.ows.wms.WebMapServer;
import org.opengis.util.ProgressListener;

@SuppressWarnings("serial")
public class WMSStoreInfoImpl extends HTTPStoreInfoImpl implements WMSStoreInfo {

    public static final int DEFAULT_MAX_CONNECTIONS = 6;

    public static final int DEFAULT_CONNECT_TIMEOUT = 30;

    public static final int DEFAULT_READ_TIMEOUT = 60;

    protected WMSStoreInfoImpl() {}

    public WMSStoreInfoImpl(Catalog catalog) {
        super(catalog);
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public WebMapServer getWebMapServer(ProgressListener listener) throws IOException {
        return getCatalog().getResourcePool().getWebMapServer(this);
    }

    @Override
    public int hashCode() {
        return WMSStoreInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return WMSStoreInfo.equals(this, obj);
    }
}

/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.IOException;
import java.util.Objects;
import org.geotools.ows.wmts.WebMapTileServer;
import org.opengis.util.ProgressListener;

/**
 * A store backed by a {@link WebMapTileServer}, allows for WMTS cascading
 *
 * @author ian
 * @author Emanuele Tajariol (etj at geo-solutions dot it)
 */
public interface WMTSStoreInfo extends HTTPStoreInfo {

    /**
     * Returns the underlying {@link WebMapTileServer}.
     *
     * <p>This method does I/O and is potentially blocking. The <tt>listener</tt> may be used to
     * report the progress of loading the datastore and also to report any errors or warnings that
     * occur.
     *
     * @param listener A progress listener, may be <code>null</code>.
     * @return The datastore.
     * @throws IOException Any I/O problems.
     */
    WebMapTileServer getWebMapTileServer(ProgressListener listener) throws IOException;

    String getHeaderName();

    void setHeaderName(String headerName);

    String getHeaderValue();

    void setHeaderValue(String headerValue);

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code WMTSStoreInfo} based on the
     * interface accessors
     */
    public static int hashCode(WMTSStoreInfo o) {
        final int prime = 31;
        return prime * StoreInfo.hashCode(o)
                + Objects.hash(
                        o.getCapabilitiesURL(),
                        o.getConnectTimeout(),
                        o.getHeaderName(),
                        o.getHeaderValue(),
                        o.getMaxConnections(),
                        o.getPassword(),
                        o.getReadTimeout(),
                        o.getUsername());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code WMTSStoreInfo} and
     * another object based on the interface accessors
     */
    public static boolean equals(WMTSStoreInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof WMTSStoreInfo)) return false;
        WMTSStoreInfo other = (WMTSStoreInfo) obj;
        return StoreInfo.equals(o, other)
                && Objects.equals(o.getCapabilitiesURL(), other.getCapabilitiesURL())
                && o.getConnectTimeout() == other.getConnectTimeout()
                && Objects.equals(o.getHeaderName(), other.getHeaderName())
                && Objects.equals(o.getHeaderValue(), other.getHeaderValue())
                && o.getMaxConnections() == other.getMaxConnections()
                && Objects.equals(o.getPassword(), other.getPassword())
                && o.getReadTimeout() == other.getReadTimeout()
                && Objects.equals(o.getUsername(), other.getUsername());
    }
}

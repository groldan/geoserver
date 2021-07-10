/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.IOException;
import java.util.Objects;
import org.geotools.ows.wms.WebMapServer;
import org.opengis.util.ProgressListener;

/**
 * A store backed by a {@link WebMapServer}, allows for WMS cascading
 *
 * @author Andrea Aime - OpenGeo
 */
public interface WMSStoreInfo extends HTTPStoreInfo {

    /**
     * Returns the underlying {@link WebMapServer}
     *
     * <p>This method does I/O and is potentially blocking. The <tt>listener</tt> may be used to
     * report the progress of loading the datastore and also to report any errors or warnings that
     * occur.
     *
     * @param listener A progress listener, may be <code>null</code>.
     * @return The datastore.
     * @throws IOException Any I/O problems.
     */
    WebMapServer getWebMapServer(ProgressListener listener) throws IOException;

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code WMSStoreInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(WMSStoreInfo o) {
        final int prime = 31;
        return prime * HTTPStoreInfo.hashCode(o)
                + Objects.hash(
                        o.getCapabilitiesURL(),
                        o.getConnectTimeout(),
                        o.getMaxConnections(),
                        o.getPassword(),
                        o.getReadTimeout(),
                        o.getUsername());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code WMSStoreInfo} and
     * another object based on the interface accessors
     *
     * @since 20.0
     */
    public static boolean equals(WMSStoreInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof WMSStoreInfo)) return false;
        WMSStoreInfo other = (WMSStoreInfo) obj;
        return HTTPStoreInfo.equals(o, other)
                && o.getConnectTimeout() == other.getConnectTimeout()
                && o.getMaxConnections() == other.getMaxConnections()
                && o.getReadTimeout() == other.getReadTimeout()
                && Objects.equals(o.getCapabilitiesURL(), other.getCapabilitiesURL())
                && Objects.equals(o.getUsername(), other.getUsername())
                && Objects.equals(o.getPassword(), other.getPassword());
    }
}

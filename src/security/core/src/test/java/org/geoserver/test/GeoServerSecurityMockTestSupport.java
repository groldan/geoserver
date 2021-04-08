/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.test;

import org.geoserver.data.test.MockCreator;
import org.geoserver.data.test.MockSecurityTestData;
import org.geoserver.data.test.MockTestData;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.password.GeoServerDigestPasswordEncoder;
import org.geoserver.security.password.GeoServerPBEPasswordEncoder;
import org.geoserver.security.password.GeoServerPlainTextPasswordEncoder;

/**
 * Extends {@link GeoServerMockTestSupport} adding {@link #getSecurityManager()}
 */
public class GeoServerSecurityMockTestSupport extends GeoServerMockTestSupport {

    @Override
    protected MockSecurityTestData createTestData() throws Exception {
        return new MockSecurityTestData();
    }

    @Override
    protected MockSecurityTestData getTestData() {
        return (MockSecurityTestData) super.getTestData();
    }

    public GeoServerSecurityManager getSecurityManager() {
        return getTestData().getSecurityManager();
    }

    /** Accessor for plain text password encoder. */
    protected GeoServerPlainTextPasswordEncoder getPlainTextPasswordEncoder() {
        return getSecurityManager().loadPasswordEncoder(GeoServerPlainTextPasswordEncoder.class);
    }

    /** Accessor for digest password encoder. */
    protected GeoServerDigestPasswordEncoder getDigestPasswordEncoder() {
        return getSecurityManager().loadPasswordEncoder(GeoServerDigestPasswordEncoder.class);
    }

    /** Accessor for regular (weak encryption) pbe password encoder. */
    protected GeoServerPBEPasswordEncoder getPBEPasswordEncoder() {
        return getSecurityManager()
                .loadPasswordEncoder(GeoServerPBEPasswordEncoder.class, null, false);
    }

    /** Accessor for strong encryption pbe password encoder. */
    protected GeoServerPBEPasswordEncoder getStrongPBEPasswordEncoder() {
        return getSecurityManager()
                .loadPasswordEncoder(GeoServerPBEPasswordEncoder.class, null, true);
    }

    /** Forwards through to {@link MockTestData#setMockCreator(MockCreator)} */
    protected void setMockCreator(MockCreator mockCreator) {
        getTestData().setMockCreator(mockCreator);
    }
}

/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.oauth2;

import org.geoserver.config.util.XStreamPersister;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.filter.GeoServerSecurityFilter;

/** @author Alessio Fabiani, GeoSolutions S.A.S. */
public class GeoNodeOAuth2AuthenticationProvider extends GeoServerOAuthAuthenticationProvider {

    public GeoNodeOAuth2AuthenticationProvider(
            GeoServerSecurityManager securityManager,
            String tokenServices,
            String oauth2SecurityConfiguration,
            String geoServerOauth2RestTemplate) {
        super(securityManager, tokenServices, oauth2SecurityConfiguration, geoServerOauth2RestTemplate);
    }

    @Override
    public void configure(XStreamPersister xp) {
        xp.getXStream().alias("geoNodeOauth2Authentication", GeoNodeOAuth2FilterConfig.class);
    }

    @Override
    public Class<? extends GeoServerSecurityFilter> getFilterClass() {
        return GeoNodeOAuthAuthenticationFilter.class;
    }

    @Override
    public GeoServerSecurityFilter createFilter(SecurityNamedServiceConfig config) {
        return new GeoNodeOAuthAuthenticationFilter(
                config, tokenServices, oauth2SecurityConfiguration, geoServerOauth2RestTemplate);
    }
}

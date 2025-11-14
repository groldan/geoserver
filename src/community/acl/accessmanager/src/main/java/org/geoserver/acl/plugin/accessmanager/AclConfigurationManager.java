/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoServer 2.24-SNAPSHOT under GPL 2.0 license
 */
package org.geoserver.acl.plugin.accessmanager;

import java.util.Objects;
import org.geoserver.acl.authorization.AccessRequest;
import org.geoserver.acl.authorization.AuthorizationService;

/** @author ETj (etj at geo-solutions.it) - Originally as part of GeoFence's GeoServer extension */
public class AclConfigurationManager implements AccessManagerConfigProvider {

    private AccessManagerConfig accessManagerConfiguration = new AccessManagerConfig();

    private AuthorizationService service;

    public AclConfigurationManager(AuthorizationService service) {
        Objects.requireNonNull(service);
        this.service = service;
    }

    @Override
    public AccessManagerConfig get() {
        return getConfiguration();
    }

    public AccessManagerConfig getConfiguration() {
        return accessManagerConfiguration;
    }

    /** Updates the configuration. */
    public void setConfiguration(AccessManagerConfig configuration) {
        this.accessManagerConfiguration = configuration;
    }

    public void testConfig(AccessManagerConfig config) {
        service.getMatchingRules(AccessRequest.builder().build());
    }
}

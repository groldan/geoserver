/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.acl.plugin.config.accessmanager;

import org.geoserver.acl.authorization.AuthorizationService;
import org.geoserver.acl.plugin.accessmanager.ACLDispatcherCallback;
import org.geoserver.acl.plugin.accessmanager.ACLResourceAccessManager;
import org.geoserver.acl.plugin.accessmanager.AccessManagerConfig;
import org.geoserver.acl.plugin.accessmanager.wps.WPSHelper;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.impl.LocalWorkspaceCatalog;
import org.geoserver.security.impl.LayerGroupContainmentCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AccessManagerSpringConfig {

    @Bean
    AccessManagerConfig aclConfig(Environment env) {
        AccessManagerConfig config = new AccessManagerConfig();
        String serviceUrl = env.getProperty("geoserver.acl.client.basePath");
        config.setServiceUrl(serviceUrl);
        return config;
    }

    @Bean
    ACLResourceAccessManager aclAccessManager(
            AuthorizationService aclService,
            AccessManagerConfig configuration,
            LayerGroupContainmentCache groupsCache,
            WPSHelper wpsHelper) {

        return new ACLResourceAccessManager(aclService, groupsCache, configuration, wpsHelper);
    }

    @Bean
    ACLDispatcherCallback aclDispatcherCallback(
            AuthorizationService aclAuthorizationService, @Qualifier("rawCatalog") Catalog rawCatalog) {

        LocalWorkspaceCatalog localWorkspaceCatalog = new LocalWorkspaceCatalog(rawCatalog);
        return new ACLDispatcherCallback(aclAuthorizationService, localWorkspaceCatalog);
    }

    @Bean
    WPSHelper aclWpsHelper(AuthorizationService aclAuthService) {
        return new WPSHelper(aclAuthService);
    }
}

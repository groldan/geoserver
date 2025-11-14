/* (c) 2023 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoServer 2.24-SNAPSHOT under GPL 2.0 license
 */
package org.geoserver.acl.plugin.accessmanager;

import java.io.Serializable;

/**
 * Configuration object for {@link ACLResourceAccessManager}.
 *
 * @author "Mauro Bartolomeoli - mauro.bartolomeoli@geo-solutions.it" - Originally as part of GeoFence's GeoServer
 *     extension
 */
@SuppressWarnings("serial")
public class AccessManagerConfig implements Serializable, Cloneable {

    public static final String URL_INTERNAL = "internal:/";

    private boolean grantWriteToWorkspacesToAuthenticatedUsers;

    private String serviceUrl = URL_INTERNAL;

    public AccessManagerConfig() {
        initDefaults();
    }

    public void initDefaults() {
        grantWriteToWorkspacesToAuthenticatedUsers = false;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Whether to allow write access to resources to authenticated users ({@code true}, if {@code false}, only admins
     * (users with {@literal ROLE_ADMINISTRATOR}) have write access.
     */
    boolean isGrantWriteToWorkspacesToAuthenticatedUsers() {
        return grantWriteToWorkspacesToAuthenticatedUsers;
    }

    /**
     * Whether to allow write access to resources to authenticated users, if false only admins (users with
     * {@literal ROLE_ADMINISTRATOR}) have write access.
     */
    void setGrantWriteToWorkspacesToAuthenticatedUsers(boolean grantWriteToWorkspacesToAuthenticatedUsers) {
        this.grantWriteToWorkspacesToAuthenticatedUsers = grantWriteToWorkspacesToAuthenticatedUsers;
    }

    /** Creates a copy of the configuration object. */
    @Override
    public AccessManagerConfig clone() {
        try {
            return (AccessManagerConfig) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new UnknownError("Unexpected exception: " + ex.getMessage());
        }
    }
}

/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.SystemUtils;
import org.geoserver.security.impl.DataAccessRule;
import org.geoserver.security.impl.DataAccessRuleDAO;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.GeoServerUser;
import org.geoserver.security.impl.GeoServerUserGroup;
import org.geoserver.security.password.GeoServerDigestPasswordEncoder;
import org.geoserver.security.password.GeoServerPBEPasswordEncoder;
import org.geoserver.security.password.GeoServerPlainTextPasswordEncoder;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geoserver.test.TestSetupFrequency;

/**
 * Test support class providing additional accessors for security related beans.
 *
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoServerSecurityTestSupport extends GeoServerSystemTestSupport {

    /** Accessor for the geoserver master password. */
    protected String getMasterPassword() {
        return new String(getSecurityManager().getMasterPassword());
    }

    @Override
    protected TestSetupFrequency lookupTestSetupPolicy() {
        // if it's windows the file system locks are causing random failures,
        // switch to a full re-setup of the data directory instead
        if (SystemUtils.IS_OS_WINDOWS) {
            return TestSetupFrequency.REPEAT;
        }
        return super.lookupTestSetupPolicy();
    }

    /** Accesssor for global security manager instance from the test application context. */
    protected GeoServerSecurityManager getSecurityManager() {
        return (GeoServerSecurityManager) applicationContext.getBean("geoServerSecurityManager");
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

    protected void addUser(
            String username, String password, List<String> groups, List<String> roles)
            throws Exception {
        GeoServerSecurityManager secMgr = getSecurityManager();
        GeoServerUserGroupService ugService = secMgr.loadUserGroupService("default");

        GeoServerUserGroupStore ugStore = ugService.createStore();
        GeoServerUser user = ugStore.createUserObject(username, password, true);
        ugStore.addUser(user);

        if (groups != null && !groups.isEmpty()) {
            for (String groupName : groups) {
                GeoServerUserGroup group = ugStore.getGroupByGroupname(groupName);
                if (group == null) {
                    group = ugStore.createGroupObject(groupName, true);
                    ugStore.addGroup(group);
                }

                ugStore.associateUserToGroup(user, group);
            }
        }
        ugStore.store();

        if (roles != null && !roles.isEmpty()) {
            GeoServerRoleService roleService = secMgr.getActiveRoleService();
            GeoServerRoleStore roleStore = roleService.createStore();
            for (String roleName : roles) {
                GeoServerRole role = roleStore.getRoleByName(roleName);
                if (role == null) {
                    role = roleStore.createRoleObject(roleName);
                    roleStore.addRole(role);
                }

                roleStore.associateRoleToUser(role, username);
            }

            roleStore.store();
        }
    }

    protected void addLayerAccessRule(
            String workspace, String layer, AccessMode mode, String... roles) throws IOException {
        DataAccessRuleDAO dao = DataAccessRuleDAO.get();
        DataAccessRule rule = new DataAccessRule();
        rule.setRoot(workspace);
        rule.setLayer(layer);
        rule.setAccessMode(mode);
        rule.getRoles().addAll(Arrays.asList(roles));
        dao.addRule(rule);
        dao.storeRules();
    }

}

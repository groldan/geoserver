/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.impl;

import org.junit.Test;

public class HashCodeTest {
    private EqualsAndHashCodeTestSupport support = new EqualsAndHashCodeTestSupport();

    @Test
    public void hashCodeWorkspaceInfo() {
        support.testHashCode(WorkspaceInfoImpl.class);
    }

    @Test
    public void hashCodeNamespaceInfo() {
        support.testHashCode(NamespaceInfoImpl.class);
    }

    @Test
    public void hashCodeMapInfo() {
        support.testHashCode(MapInfoImpl.class);
    }

    @Test
    public void hashCodeStyleInfo() {
        support.testHashCode(StyleInfoImpl.class);
    }

    @Test
    public void hashCodeLayerInfo() {
        support.testHashCode(LayerInfoImpl.class);
    }

    @Test
    public void hashCodeLayerGroupInfo() {
        support.testHashCode(LayerGroupInfoImpl.class);
    }

    @Test
    public void hashCodeCoverageStoreInfo() {
        support.testHashCode(CoverageStoreInfoImpl.class);
    }

    @Test
    public void hashCodeDataStoreInfo() {
        support.testHashCode(DataStoreInfoImpl.class);
    }

    @Test
    public void hashCodeWMSStoreInfo() {
        support.testHashCode(WMSStoreInfoImpl.class);
    }

    @Test
    public void hashCodeWMTSStoreInfo() {
        support.testHashCode(WMTSStoreInfoImpl.class);
    }

    @Test
    public void hashCodeCoverageInfo() {
        support.testHashCode(CoverageInfoImpl.class);
    }

    @Test
    public void hashCodeFeatureTypeInfo() {
        support.testHashCode(FeatureTypeInfoImpl.class);
    }

    @Test
    public void hashCodeWMSLayerInfo() {
        support.testHashCode(WMSLayerInfoImpl.class);
    }

    @Test
    public void hashCodeWMTSLayerInfo() {
        support.testHashCode(WMTSLayerInfoImpl.class);
    }

}

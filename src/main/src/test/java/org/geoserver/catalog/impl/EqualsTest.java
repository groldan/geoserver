/*
 * (c) 2021 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.impl;

import org.junit.Test;

public class EqualsTest {
    private EqualsAndHashCodeTestSupport support = new EqualsAndHashCodeTestSupport();

    @Test
    public void equalsWorkspaceInfo() {
        support.testEquals(WorkspaceInfoImpl.class);
    }

    @Test
    public void equalsNamespaceInfo() {
        support.testEquals(NamespaceInfoImpl.class);
    }

    @Test
    public void equalsMapInfo() {
        support.testEquals(MapInfoImpl.class);
    }

    @Test
    public void equalsStyleInfo() {
        support.testEquals(StyleInfoImpl.class);
    }

    @Test
    public void equalsLayerInfo() {
        support.testEquals(LayerInfoImpl.class);
    }

    @Test
    public void equalsLayerGroupInfo() {
        support.testEquals(LayerGroupInfoImpl.class);
    }

    @Test
    public void equalsCoverageStoreInfo() {
        support.testEquals(CoverageStoreInfoImpl.class);
    }

    @Test
    public void equalsDataStoreInfo() {
        support.testEquals(DataStoreInfoImpl.class);
    }

    @Test
    public void equalsWMSStoreInfo() {
        support.testEquals(WMSStoreInfoImpl.class);
    }

    @Test
    public void equalsWMTSStoreInfo() {
        support.testEquals(WMTSStoreInfoImpl.class);
    }

    @Test
    public void equalsCoverageInfo() {
        support.testEquals(CoverageInfoImpl.class);
    }

    @Test
    public void equalsFeatureTypeInfo() {
        support.testEquals(FeatureTypeInfoImpl.class);
    }

    @Test
    public void equalsWMSLayerInfo() {
        support.testEquals(WMSLayerInfoImpl.class);
    }

    @Test
    public void equalsWMTSLayerInfo() {
        support.testEquals(WMTSLayerInfoImpl.class);
    }

}

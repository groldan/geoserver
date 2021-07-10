/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.util.List;
import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.StoreInfo;
import org.geotools.data.FeatureSource;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.measure.Measure;
import org.geotools.util.factory.Hints;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.util.ProgressListener;

@SuppressWarnings("serial")
public class FeatureTypeInfoImpl extends ResourceInfoImpl implements FeatureTypeInfo {

    protected transient Filter filter;

    protected String cqlFilter;

    protected int maxFeatures;
    protected int numDecimals;
    protected boolean padWithZeros;
    protected boolean forcedDecimal;

    protected List<AttributeTypeInfo> attributes;
    protected List<String> responseSRS;

    boolean overridingServiceSRS;
    boolean skipNumberMatched = false;
    boolean circularArcPresent;

    // we don't use the primitive because we need to detect the situation where no value was set
    Boolean encodeMeasures;

    @Override
    public boolean isCircularArcPresent() {
        return circularArcPresent;
    }

    @Override
    public void setCircularArcPresent(boolean curveGeometryEnabled) {
        this.circularArcPresent = curveGeometryEnabled;
    }

    Measure linearizationTolerance;

    protected FeatureTypeInfoImpl() {}

    public FeatureTypeInfoImpl(Catalog catalog) {
        super(catalog);
    }

    public FeatureTypeInfoImpl(Catalog catalog, String id) {
        super(catalog, id);
    }

    @Override
    public DataStoreInfo getStore() {
        StoreInfo storeInfo = super.getStore();
        if (!(storeInfo instanceof DataStoreInfo)) {
            LOGGER.warning("Failed to load actual store for " + this);
            return null;
        }
        return (DataStoreInfo) super.getStore();
    }

    @Override
    public List<AttributeTypeInfo> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeTypeInfo> attributes) {
        this.attributes = attributes;
    }

    /*
     * The filter is computed by current cqlFilter
     */
    @Override
    public Filter filter() {
        try {
            if (filter == null && cqlFilter != null && !cqlFilter.isEmpty()) {
                filter = ECQL.toFilter(cqlFilter);
            }
        } catch (CQLException e) {
            throw new org.geoserver.platform.ServiceException(
                    "Failed to generate filter from ECQL string " + e.getMessage());
        }
        return filter;
    }

    @Override
    public int getMaxFeatures() {
        return maxFeatures;
    }

    @Override
    public void setMaxFeatures(int maxFeatures) {
        this.maxFeatures = maxFeatures;
    }

    @Override
    public int getNumDecimals() {
        return numDecimals;
    }

    @Override
    public void setNumDecimals(int numDecimals) {
        this.numDecimals = numDecimals;
    }

    @Override
    public List<AttributeTypeInfo> attributes() throws IOException {
        return catalog.getResourcePool().getAttributes(this);
    }

    @Override
    public FeatureType getFeatureType() throws IOException {
        return catalog.getResourcePool().getFeatureType(this);
    }

    @Override
    public FeatureSource<? extends FeatureType, ? extends Feature> getFeatureSource(
            ProgressListener listener, Hints hints) throws IOException {
        return catalog.getResourcePool().getFeatureSource(this, hints);
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public List<String> getResponseSRS() {
        return responseSRS;
    }

    public void setResponseSRS(List<String> otherSrs) {
        this.responseSRS = otherSrs;
    }

    @Override
    public boolean isOverridingServiceSRS() {
        return overridingServiceSRS;
    }

    @Override
    public void setOverridingServiceSRS(boolean overridingServiceSRS) {
        this.overridingServiceSRS = overridingServiceSRS;
    }

    @Override
    public boolean getSkipNumberMatched() {
        return skipNumberMatched;
    }

    @Override
    public void setSkipNumberMatched(boolean skipNumberMatched) {
        this.skipNumberMatched = skipNumberMatched;
    }

    @Override
    public int hashCode() {
        return FeatureTypeInfo.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return FeatureTypeInfo.equals(this, obj);
    }

    @Override
    public Measure getLinearizationTolerance() {
        return linearizationTolerance;
    }

    @Override
    public void setLinearizationTolerance(Measure tolerance) {
        this.linearizationTolerance = tolerance;
    }

    @Override
    public String getCqlFilter() {
        return cqlFilter;
    }

    @Override
    public void setCqlFilter(String cqlFilter) {
        this.cqlFilter = cqlFilter;
        this.filter = null;
    }

    @Override
    public boolean getEncodeMeasures() {
        // by default encoding of coordinates measures is not activated
        return encodeMeasures == null ? false : encodeMeasures;
    }

    @Override
    public void setEncodeMeasures(boolean encodeMeasures) {
        this.encodeMeasures = encodeMeasures;
    }

    @Override
    public boolean getPadWithZeros() {
        return padWithZeros;
    }

    @Override
    public void setPadWithZeros(boolean padWithZeros) {
        this.padWithZeros = padWithZeros;
    }

    @Override
    public boolean getForcedDecimal() {
        return forcedDecimal;
    }

    @Override
    public void setForcedDecimal(boolean forcedDecimal) {
        this.forcedDecimal = forcedDecimal;
    }
}

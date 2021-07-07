/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageDimensionInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.ProjectionPolicy;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.factory.Hints;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.coverage.grid.GridGeometry;
import org.opengis.util.ProgressListener;

/**
 * Default Implementation of the {@link CoverageInfo} bean to capture information about a coverage.
 *
 * @author Simone Giannecchini, GeoSolutions SAS
 */
@SuppressWarnings("deprecation")
public class CoverageInfoImpl extends ResourceInfoImpl implements CoverageInfo {

    /** */
    private static final long serialVersionUID = 659498790758954330L;

    protected String nativeFormat;

    protected GridGeometry grid;

    protected List<String> supportedFormats = new ArrayList<>();

    protected List<String> interpolationMethods = new ArrayList<>();

    protected String defaultInterpolationMethod;

    protected List<CoverageDimensionInfo> dimensions = new ArrayList<>();

    protected List<String> requestSRS = new ArrayList<>();

    protected List<String> responseSRS = new ArrayList<>();

    protected Map<String, Serializable> parameters = new HashMap<>();

    protected String nativeCoverageName;

    protected CoverageInfoImpl() {}

    public CoverageInfoImpl(Catalog catalog) {
        super(catalog);
    }

    public CoverageInfoImpl(Catalog catalog, String id) {
        super(catalog, id);
    }

    @Override
    public CoverageStoreInfo getStore() {
        return (CoverageStoreInfo) super.getStore();
    }

    @Override
    public GridGeometry getGrid() {
        return grid;
    }

    @Override
    public void setGrid(GridGeometry grid) {
        this.grid = grid;
    }

    @Override
    public String getNativeFormat() {
        return nativeFormat;
    }

    @Override
    public void setNativeFormat(String nativeFormat) {
        this.nativeFormat = nativeFormat;
    }

    @Override
    public List<String> getSupportedFormats() {
        return supportedFormats;
    }

    @Override
    public List<String> getInterpolationMethods() {
        return interpolationMethods;
    }

    @Override
    public String getDefaultInterpolationMethod() {
        return defaultInterpolationMethod;
    }

    @Override
    public void setDefaultInterpolationMethod(String defaultInterpolationMethod) {
        this.defaultInterpolationMethod = defaultInterpolationMethod;
    }

    @Override
    public List<CoverageDimensionInfo> getDimensions() {
        return dimensions;
    }

    @Override
    public List<String> getRequestSRS() {
        return requestSRS;
    }

    @Override
    public List<String> getResponseSRS() {
        return responseSRS;
    }

    @Override
    public Map<String, Serializable> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Serializable> parameters) {
        this.parameters = parameters;
    }

    @Override
    public GridCoverage getGridCoverage(ProgressListener listener, Hints hints) throws IOException {

        // manage projection policy
        if (this.projectionPolicy == ProjectionPolicy.FORCE_DECLARED) {
            final Hints crsHints =
                    new Hints(Hints.DEFAULT_COORDINATE_REFERENCE_SYSTEM, this.getCRS());
            if (hints != null) hints.putAll(crsHints);
            else hints = crsHints;
        }
        return catalog.getResourcePool().getGridCoverage(this, null, hints);
    }

    @Override
    public GridCoverage getGridCoverage(
            ProgressListener listener, ReferencedEnvelope envelope, Hints hints)
            throws IOException {
        // manage projection policy
        if (this.projectionPolicy == ProjectionPolicy.FORCE_DECLARED) {
            final Hints crsHints =
                    new Hints(Hints.DEFAULT_COORDINATE_REFERENCE_SYSTEM, this.getCRS());
            if (hints != null) hints.putAll(crsHints);
            else hints = crsHints;
        }
        return catalog.getResourcePool().getGridCoverage(this, envelope, hints);
    }

    @Override
    public GridCoverageReader getGridCoverageReader(ProgressListener listener, Hints hints)
            throws IOException {
        // manage projection policy
        if (this.projectionPolicy == ProjectionPolicy.FORCE_DECLARED) {
            final Hints crsHints =
                    new Hints(Hints.DEFAULT_COORDINATE_REFERENCE_SYSTEM, this.getCRS());
            if (hints != null) hints.putAll(crsHints);
            else hints = crsHints;
        }
        return catalog.getResourcePool().getGridCoverageReader(this, nativeCoverageName, hints);
    }

    public void setSupportedFormats(List<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }

    public void setInterpolationMethods(List<String> interpolationMethods) {
        this.interpolationMethods = interpolationMethods;
    }

    public void setDimensions(List<CoverageDimensionInfo> dimensions) {
        this.dimensions = dimensions;
    }

    public void setRequestSRS(List<String> requestSRS) {
        this.requestSRS = requestSRS;
    }

    public void setResponseSRS(List<String> responseSRS) {
        this.responseSRS = responseSRS;
    }

    @Override
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result =
                prime * result
                        + Objects.hash(
                                defaultInterpolationMethod,
                                dimensions,
                                grid,
                                interpolationMethods,
                                nativeCoverageName,
                                nativeFormat,
                                parameters,
                                requestSRS,
                                responseSRS,
                                supportedFormats);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (!(obj instanceof CoverageInfo)) return false;
        CoverageInfo other = (CoverageInfo) obj;
        return Objects.equals(defaultInterpolationMethod, other.getDefaultInterpolationMethod())
                && Objects.equals(dimensions, other.getDimensions())
                && Objects.equals(grid, other.getGrid())
                && Objects.equals(interpolationMethods, other.getInterpolationMethods())
                && Objects.equals(nativeCoverageName, other.getNativeCoverageName())
                && Objects.equals(nativeFormat, other.getNativeFormat())
                && Objects.equals(parameters, other.getParameters())
                && Objects.equals(requestSRS, other.getRequestSRS())
                && Objects.equals(responseSRS, other.getResponseSRS())
                && Objects.equals(supportedFormats, other.getSupportedFormats());
    }

    @Override
    public String getNativeCoverageName() {
        return nativeCoverageName;
    }

    @Override
    public void setNativeCoverageName(String nativeCoverageName) {
        this.nativeCoverageName = nativeCoverageName;
    }
}

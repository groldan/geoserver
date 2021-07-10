/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.geoserver.config.GeoServerInfo;
import org.geotools.data.FeatureSource;
import org.geotools.measure.Measure;
import org.geotools.util.factory.Hints;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.util.ProgressListener;

/**
 * A vector-based or feature based resource.
 *
 * @author Justin Deoliveira, The Open Planning Project
 * @uml.dependency supplier="org.geoserver.catalog.FeatureResource"
 */
public interface FeatureTypeInfo extends ResourceInfo {

    /** The sql view definition */
    static final String JDBC_VIRTUAL_TABLE = "JDBC_VIRTUAL_TABLE";

    /** The cascaded stored query configuration */
    static final String STORED_QUERY_CONFIGURATION = "WFS_NG_STORED_QUERY_CONFIGURATION";

    /** ONLY FOR WFS-NG Feature Types */
    static final String OTHER_SRS = "OTHER_SRS";

    /**
     * The data store the feature type is a part of.
     *
     * <p>
     */
    @Override
    DataStoreInfo getStore();

    /**
     * The attributes that the feature type exposes.
     *
     * <p>Services and client code will want to call the {@link #attributes()} method over this one.
     */
    List<AttributeTypeInfo> getAttributes();

    /**
     * A filter which should be applied to all queries of the dataset represented by the feature
     * type.
     *
     * @return A filter, or <code>null</code> if one not set.
     * @uml.property name="filter"
     */
    Filter filter();

    /**
     * A cap on the number of features that a query against this type can return.
     *
     * <p>Note that this value should override the global default: {@link
     * GeoServerInfo#getMaxFeatures()}.
     */
    int getMaxFeatures();

    /** Sets a cap on the number of features that a query against this type can return. */
    void setMaxFeatures(int maxFeatures);

    /**
     * The number of decimal places to use when encoding floating point numbers from data of this
     * feature type.
     *
     * <p>Note that this value should override the global default: {@link
     * GeoServerInfo#getNumDecimals()}.
     */
    int getNumDecimals();

    /**
     * Sets the number of decimal places to use when encoding floating point numbers from data of
     * this feature type.
     */
    void setNumDecimals(int numDecimals);

    /** If numbers float should be formatted right-padding them with zeros. */
    boolean getPadWithZeros();

    /** Sets wether float numbers should be formatted right-padding them with zeros. */
    void setPadWithZeros(boolean padWithZeros);

    /** True if numbers should always be formatted as decimal (no scientific notation allowed). */
    boolean getForcedDecimal();

    /**
     * Set to true if numbers should always be formatted as decimal (no scientific notation
     * allowed).
     */
    void setForcedDecimal(boolean forcedDecimal);

    /**
     * Tolerance used to linearize this feature type, as an absolute value expressed in the
     * geometries own CRS
     */
    Measure getLinearizationTolerance();

    /**
     * Tolerance used to linearize this feature type, as an absolute value expressed in the
     * geometries own CRS
     */
    void setLinearizationTolerance(Measure tolerance);

    /** True if this feature type info is overriding the WFS global SRS list */
    boolean isOverridingServiceSRS();

    /** Set to true if this feature type info is overriding the WFS global SRS list */
    void setOverridingServiceSRS(boolean overridingServiceSRS);

    /** True if this feature type info is overriding the counting of numberMatched. */
    boolean getSkipNumberMatched();

    /**
     * Set to true if this feature type info is overriding the default counting of numberMatched.
     */
    void setSkipNumberMatched(boolean skipNumberMatched);

    /**
     * The srs's that the WFS service will advertise in the capabilities document for this feature
     * type (overriding the global WFS settings)
     */
    List<String> getResponseSRS();

    /**
     * Returns the derived set of attributes for the feature type.
     *
     * <p>This value is derived from the underlying feature, and any overrides configured via {@link
     * #getAttributes()}.
     */
    List<AttributeTypeInfo> attributes() throws IOException;

    /**
     * Returns the underlying geotools feature type.
     *
     * <p>The returned feature type is "wrapped" to take into account "metadata", such as
     * reprojection and name aliasing.
     */
    FeatureType getFeatureType() throws IOException;

    /** Return the ECQL string used as default feature type filter */
    String getCqlFilter();

    /** Set the ECQL string used as default featue type filter */
    void setCqlFilter(String cqlFilterString);

    /**
     * Returns the underlying feature source instance.
     *
     * <p>This method does I/O and is potentially blocking. The <tt>listener</tt> may be used to
     * report the progress of loading the feature source and also to report any errors or warnings
     * that occur.
     *
     * @param listener A progress listener, may be <code>null</code>.
     * @param hints Hints to use while loading the featuer source, may be <code>null</code>.
     * @return The feature source.
     * @throws IOException Any I/O problems.
     */
    FeatureSource<? extends FeatureType, ? extends Feature> getFeatureSource(
            ProgressListener listener, Hints hints) throws IOException;

    boolean isCircularArcPresent();

    void setCircularArcPresent(boolean arcsPresent);

    /**
     * Controls if coordinates measures should be included in WFS outputs.
     *
     * @return TRUE if measures should be encoded, otherwise FALSE
     */
    default boolean getEncodeMeasures() {
        // by default coordinates measures are not encoded
        return false;
    }

    /**
     * Sets if coordinates measures should be included in WFS outputs.
     *
     * @param encodeMeasures TRUE if measures should be encoded, otherwise FALSE
     */
    default void setEncodeMeasures(boolean encodeMeasures) {
        // nothing to do
    }

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code FeatureTypeInfo} based on
     * the interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(FeatureTypeInfo o) {
        final int prime = 31;
        return prime * ResourceInfo.hashCode(o)
                + Objects.hash(
                        o.getAttributes(),
                        o.isCircularArcPresent(),
                        o.getCqlFilter(),
                        o.getEncodeMeasures(),
                        o.getForcedDecimal(),
                        o.getLinearizationTolerance(),
                        o.getMaxFeatures(),
                        o.getNumDecimals(),
                        o.isOverridingServiceSRS(),
                        o.getPadWithZeros(),
                        o.getResponseSRS(),
                        o.getSkipNumberMatched());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code FeatureTypeInfo} and
     * another object based on the interface accessors
     *
     * @implNote {@link FeatureTypeInfo#getAttributes()} is compared using {@link
     *     AttributeTypeInfo#equalsIngnoreFeatureType}
     * @since 20.0
     */
    public static boolean equals(FeatureTypeInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof FeatureTypeInfo)) return false;
        FeatureTypeInfo other = (FeatureTypeInfo) obj;
        return ResourceInfo.equals(o, other)
                && attributesEquals(o.getAttributes(), other.getAttributes())
                && o.isCircularArcPresent() == other.isCircularArcPresent()
                && o.getForcedDecimal() == other.getForcedDecimal()
                && o.getMaxFeatures() == other.getMaxFeatures()
                && o.getNumDecimals() == other.getNumDecimals()
                && o.isOverridingServiceSRS() == other.isOverridingServiceSRS()
                && o.getPadWithZeros() == other.getPadWithZeros()
                && o.getSkipNumberMatched() == other.getSkipNumberMatched()
                && Objects.equals(o.getCqlFilter(), other.getCqlFilter())
                && Objects.equals(o.getEncodeMeasures(), other.getEncodeMeasures())
                && Objects.equals(o.getLinearizationTolerance(), other.getLinearizationTolerance())
                && Objects.equals(o.getResponseSRS(), other.getResponseSRS());
    }

    static boolean attributesEquals(
            List<AttributeTypeInfo> attributes, List<AttributeTypeInfo> otherAttributes) {

        if (otherAttributes == attributes) return true;
        ListIterator<AttributeTypeInfo> attributesIterator = attributes.listIterator();
        ListIterator<AttributeTypeInfo> otherAttributesIterator = otherAttributes.listIterator();
        while (attributesIterator.hasNext() && otherAttributesIterator.hasNext()) {
            AttributeTypeInfo attr = attributesIterator.next();
            AttributeTypeInfo otherAttr = otherAttributesIterator.next();

            if (attr == null) {
                if (otherAttr != null) return false;
            } else if (!attr.equalsIngnoreFeatureType(otherAttr)) {
                return false;
            }
        }
        if (attributesIterator.hasNext() || otherAttributesIterator.hasNext()) return false;
        return true;
    }
}

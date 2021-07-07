/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Objects;
import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.MetadataMap;
import org.opengis.feature.type.AttributeDescriptor;

public class AttributeTypeInfoImpl implements AttributeTypeInfo {

    // REVISIT: dead code
    protected transient String id;
    protected String name;
    protected int minOccurs;
    protected int maxOccurs;
    protected boolean nillable;
    protected transient AttributeDescriptor attribute;
    protected MetadataMap metadata = new MetadataMap();
    protected FeatureTypeInfo featureType;
    protected Class<?> binding;
    protected Integer length;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getMaxOccurs() {
        return maxOccurs;
    }

    @Override
    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    @Override
    public int getMinOccurs() {
        return minOccurs;
    }

    @Override
    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    @Override
    public boolean isNillable() {
        return nillable;
    }

    @Override
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    @Override
    public FeatureTypeInfo getFeatureType() {
        return featureType;
    }

    @Override
    public void setFeatureType(FeatureTypeInfo featureType) {
        this.featureType = featureType;
    }

    @Override
    public AttributeDescriptor getAttribute() {
        return attribute;
    }

    @Override
    public void setAttribute(AttributeDescriptor attribute) {
        this.attribute = attribute;
    }

    @Override
    public MetadataMap getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataMap metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Class<?> getBinding() {
        return binding;
    }

    @Override
    public void setBinding(Class<?> binding) {
        this.binding = binding;
    }

    @Override
    public Integer getLength() {
        return length;
    }

    @Override
    public void setLength(Integer length) {
        this.length = length;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + hashCode(binding);
        result =
                prime * result
                        + Objects.hash(
                                featureType,
                                length,
                                maxOccurs,
                                metadata,
                                minOccurs,
                                name,
                                nillable);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return this.equals(obj, false);
    }

    @Override
    public boolean equalsIngnoreFeatureType(Object obj) {
        return this.equals(obj, true);
    }

    private boolean equals(Object obj, final boolean ignoreFeatureType) {
        if (this == obj) return true;
        if (!(obj instanceof AttributeTypeInfo)) return false;
        AttributeTypeInfo other = (AttributeTypeInfo) obj;
        return equals(binding, other.getBinding())
                        && Objects.equals(attribute, other.getAttribute())
                        && Objects.equals(name, other.getName())
                        && Objects.equals(length, other.getLength())
                        && maxOccurs == other.getMaxOccurs()
                        && Objects.equals(metadata, other.getMetadata())
                        && minOccurs == other.getMinOccurs()
                        && nillable == other.isNillable()
                        && ignoreFeatureType
                ? true
                : Objects.equals(featureType, other.getFeatureType());
    }

    /**
     * Used to compare class equality using {@link Class#getCanonicalName()} for {@link #binding}
     * since {@code Class} does not implement {@code equals()} and {@code hashCode()}
     */
    private boolean equals(Class<?> c1, Class<?> c2) {
        String n1 = c1 == null ? null : c1.getCanonicalName();
        String n2 = c2 == null ? null : c2.getCanonicalName();
        return Objects.equals(n1, n2);
    }

    /**
     * Used to compare class equality using {@link Class#getCanonicalName()} for {@link #binding}
     * since {@code Class} does not implement {@code equals()} and {@code hashCode()}
     */
    private int hashCode(Class<?> c1) {
        String n1 = c1 == null ? null : c1.getCanonicalName();
        return Objects.hashCode(n1);
    }
}

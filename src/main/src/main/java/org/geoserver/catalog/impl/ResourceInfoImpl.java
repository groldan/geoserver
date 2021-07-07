/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataLinkInfo;
import org.geoserver.catalog.KeywordInfo;
import org.geoserver.catalog.MetadataLinkInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ProjectionPolicy;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.util.GeoServerDefaultLocale;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.GrowableInternationalString;
import org.geotools.util.logging.Logging;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.InternationalString;

/** Default implementation of {@link ResourceInfo}. */
@SuppressWarnings("serial")
public abstract class ResourceInfoImpl implements ResourceInfo {

    static final Logger LOGGER = Logging.getLogger(ResourceInfoImpl.class);

    protected String id;

    protected String name;

    protected String nativeName;

    protected List<String> alias = new ArrayList<>();

    protected NamespaceInfo namespace;

    protected String title;

    protected String description;

    protected String _abstract;

    protected List<KeywordInfo> keywords = new ArrayList<>();

    protected List<MetadataLinkInfo> metadataLinks = new ArrayList<>();

    protected List<DataLinkInfo> dataLinks = new ArrayList<>();

    protected CoordinateReferenceSystem nativeCRS;

    protected String srs;

    protected ReferencedEnvelope nativeBoundingBox;

    protected ReferencedEnvelope latLonBoundingBox;

    protected ProjectionPolicy projectionPolicy;

    protected boolean enabled;

    protected Boolean advertised;

    protected MetadataMap metadata = new MetadataMap();

    protected StoreInfo store;

    protected boolean serviceConfiguration = false;

    protected List<String> disabledServices = new ArrayList<>();

    protected Boolean simpleConversionEnabled = false;

    protected transient Catalog catalog;

    protected GrowableInternationalString internationalTitle;

    protected GrowableInternationalString internationalAbstract;

    protected ResourceInfoImpl() {}

    protected ResourceInfoImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    protected ResourceInfoImpl(Catalog catalog, String id) {
        this(catalog);
        setId(id);
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public void setCatalog(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** @see org.geoserver.catalog.ResourceInfo#getQualifiedName() */
    @Override
    public Name getQualifiedName() {
        return new NameImpl(getNamespace().getURI(), getName());
    }

    @Override
    public String getNativeName() {
        return nativeName;
    }

    @Override
    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    /** @see org.geoserver.catalog.ResourceInfo#getQualifiedNativeName() */
    @Override
    public Name getQualifiedNativeName() {
        return new NameImpl(getNamespace().getURI(), getNativeName());
    }

    @Override
    public NamespaceInfo getNamespace() {
        return namespace;
    }

    @Override
    public void setNamespace(NamespaceInfo namespace) {
        this.namespace = namespace;
    }

    @Override
    public String prefixedName() {
        return getNamespace().getPrefix() + ":" + getName();
    }

    @Override
    public String getTitle() {
        if (title == null && internationalTitle != null) {
            return internationalTitle.toString(GeoServerDefaultLocale.get());
        } else return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getAbstract() {
        if (_abstract == null && internationalAbstract != null)
            return internationalAbstract.toString(GeoServerDefaultLocale.get());
        else return _abstract;
    }

    @Override
    public void setAbstract(String _abstract) {
        this._abstract = _abstract;
    }

    @Override
    public List<KeywordInfo> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<KeywordInfo> keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywordValues() {
        List<String> values = new ArrayList<>();
        if (keywords != null) {
            for (KeywordInfo kw : keywords) {
                values.add(kw.getValue());
            }
        }
        return values;
    }

    @Override
    public List<MetadataLinkInfo> getMetadataLinks() {
        return metadataLinks;
    }

    @Override
    public List<DataLinkInfo> getDataLinks() {
        return dataLinks;
    }

    @Override
    public String getSRS() {
        return srs;
    }

    @Override
    public void setSRS(String srs) {
        this.srs = srs;
    }

    @Override
    public ReferencedEnvelope boundingBox() throws Exception {
        CoordinateReferenceSystem declaredCRS = getCRS();
        CoordinateReferenceSystem nativeCRS = getNativeCRS();
        ProjectionPolicy php = getProjectionPolicy();

        ReferencedEnvelope nativeBox = this.nativeBoundingBox;
        if (nativeBox == null) {
            // back project from lat lon
            try {
                nativeBox = getLatLonBoundingBox().transform(declaredCRS, true);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to derive native bbox from declared one", e);
                return null;
            }
        }

        ReferencedEnvelope result;
        if (!CRS.equalsIgnoreMetadata(declaredCRS, nativeCRS)
                && php == ProjectionPolicy.REPROJECT_TO_DECLARED) {
            result = nativeBox.transform(declaredCRS, true);
        } else if (php == ProjectionPolicy.FORCE_DECLARED) {
            result = ReferencedEnvelope.create((Envelope) nativeBox, declaredCRS);
        } else {
            result = nativeBox;
        }

        // make sure that in no case the actual field value is returned to the client, this
        // is not a getter, it's a derivative, thus ModificationProxy won't do a copy on its own
        return ReferencedEnvelope.create(result);
    }

    @Override
    public ReferencedEnvelope getLatLonBoundingBox() {
        return latLonBoundingBox;
    }

    @Override
    public void setLatLonBoundingBox(ReferencedEnvelope box) {
        this.latLonBoundingBox = box;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** @see ResourceInfo#enabled() */
    @Override
    public boolean enabled() {
        StoreInfo store = getStore();
        boolean storeEnabled = store != null && store.isEnabled();
        return storeEnabled && this.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public MetadataMap getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataMap metaData) {
        this.metadata = metaData;
    }

    public void setMetadataLinks(List<MetadataLinkInfo> metaDataLinks) {
        this.metadataLinks = metaDataLinks;
    }

    public void setDataLinks(List<DataLinkInfo> dataLinks) {
        this.dataLinks = dataLinks;
    }

    @Override
    public StoreInfo getStore() {
        return store;
    }

    @Override
    public void setStore(StoreInfo store) {
        this.store = store;
    }

    @Override
    public <T extends Object> T getAdapter(Class<T> adapterClass, Map<?, ?> hints) {
        // subclasses should override
        return null;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append('[')
                .append(name)
                .append(']')
                .toString();
    }

    @Override
    public List<String> getAlias() {
        return alias;
    }

    public void setAlias(List<String> alias) {
        this.alias = alias;
    }

    @Override
    public CoordinateReferenceSystem getCRS() {
        if (getSRS() == null) {
            return null;
        }

        // TODO: cache this
        try {
            return CRS.decode(getSRS());
        } catch (Exception e) {
            throw new RuntimeException(
                    "This is unexpected, the layer seems to be mis-configured", e);
        }
    }

    @Override
    public ReferencedEnvelope getNativeBoundingBox() {
        return nativeBoundingBox;
    }

    @Override
    public void setNativeBoundingBox(ReferencedEnvelope box) {
        this.nativeBoundingBox = box;
    }

    @Override
    public CoordinateReferenceSystem getNativeCRS() {
        return nativeCRS;
    }

    @Override
    public void setNativeCRS(CoordinateReferenceSystem nativeCRS) {
        this.nativeCRS = nativeCRS;
    }

    @Override
    public ProjectionPolicy getProjectionPolicy() {
        return projectionPolicy;
    }

    @Override
    public void setProjectionPolicy(ProjectionPolicy projectionPolicy) {
        this.projectionPolicy = projectionPolicy;
    }

    @Override
    public boolean isAdvertised() {
        if (this.advertised != null) {
            return advertised;
        }

        // check the metadata map for backwards compatibility with 2.1.x series
        MetadataMap md = getMetadata();
        if (md == null) {
            return true;
        }
        Boolean metadataAdvertised = md.get(LayerInfoImpl.KEY_ADVERTISED, Boolean.class);
        if (metadataAdvertised == null) {
            metadataAdvertised = true;
        }
        return metadataAdvertised;
    }

    @Override
    public void setAdvertised(boolean advertised) {
        this.advertised = advertised;
    }

    @Override
    public boolean isServiceConfiguration() {
        return serviceConfiguration;
    }

    @Override
    public void setServiceConfiguration(boolean serviceConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
    }

    @Override
    public List<String> getDisabledServices() {
        return disabledServices;
    }

    @Override
    public void setDisabledServices(List<String> disabledServices) {
        this.disabledServices = disabledServices;
    }

    @Override
    public boolean isSimpleConversionEnabled() {
        return simpleConversionEnabled == null ? false : simpleConversionEnabled;
    }

    @Override
    public void setSimpleConversionEnabled(boolean simpleConversionEnabled) {
        this.simpleConversionEnabled = simpleConversionEnabled;
    }

    @Override
    public GrowableInternationalString getInternationalTitle() {
        if (this.internationalTitle == null || this.internationalTitle.toString().equals("")) {
            return new GrowableInternationalString(title);
        }
        return this.internationalTitle;
    }

    @Override
    public void setInternationalTitle(InternationalString internationalTitle) {
        GrowableInternationalString growable;
        if (internationalTitle == null) growable = new GrowableInternationalString(getTitle());
        else growable = new GrowableInternationalString(internationalTitle);
        this.internationalTitle = growable;
    }

    @Override
    public InternationalString getInternationalAbstract() {
        if (this.internationalAbstract == null || this.internationalAbstract.toString().equals(""))
            return new GrowableInternationalString(_abstract);
        return this.internationalAbstract;
    }

    @Override
    public void setInternationalAbstract(InternationalString internationalAbstract) {
        GrowableInternationalString growable;
        if (internationalAbstract == null)
            growable = new GrowableInternationalString(getAbstract());
        else growable = new GrowableInternationalString(internationalAbstract);
        this.internationalAbstract = growable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                _abstract,
                advertised,
                alias,
                dataLinks,
                description,
                disabledServices,
                enabled,
                id,
                internationalAbstract,
                internationalTitle,
                keywords,
                latLonBoundingBox,
                metadata,
                metadataLinks,
                name,
                namespace,
                nativeBoundingBox,
                nativeCRS,
                nativeName,
                projectionPolicy,
                serviceConfiguration,
                simpleConversionEnabled,
                srs,
                store,
                title);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ResourceInfo)) return false;
        ResourceInfo other = (ResourceInfo) obj;
        // Note: using accessors instead of direct field access. Some properties are computed on the
        // fly (e.g. isAdvertised())
        return Objects.equals(id, other.getId())
                && isEnabled() == other.isEnabled()
                && getProjectionPolicy() == other.getProjectionPolicy()
                && isServiceConfiguration() == other.isServiceConfiguration()
                && Objects.equals(getAbstract(), other.getAbstract())
                && Objects.equals(isAdvertised(), other.isAdvertised())
                && Objects.equals(getAlias(), other.getAlias())
                && Objects.equals(getDataLinks(), other.getDataLinks())
                && Objects.equals(getDescription(), other.getDescription())
                && Objects.equals(getDisabledServices(), other.getDisabledServices())
                && Objects.equals(getKeywords(), other.getKeywords())
                && Objects.equals(getLatLonBoundingBox(), other.getLatLonBoundingBox())
                && Objects.equals(getMetadata(), other.getMetadata())
                && Objects.equals(getMetadataLinks(), other.getMetadataLinks())
                && Objects.equals(getName(), other.getName())
                && Objects.equals(getNamespace(), other.getNamespace())
                && Objects.equals(getNativeBoundingBox(), other.getNativeBoundingBox())
                && Objects.equals(getNativeName(), other.getNativeName())
                && Objects.equals(isSimpleConversionEnabled(), other.isSimpleConversionEnabled())
                && Objects.equals(getSRS(), other.getSRS())
                && Objects.equals(getTitle(), other.getTitle())
                // CRS.equalsIgnoreMetadata accepts null on either arg
                && CRS.equalsIgnoreMetadata(getNativeCRS(), other.getNativeCRS())
                // Using getters since value may be computed on the fly
                && Objects.equals(getInternationalAbstract(), other.getInternationalAbstract())
                && Objects.equals(getInternationalTitle(), other.getInternationalTitle())
                && Objects.equals(getStore(), other.getStore());
    }
}

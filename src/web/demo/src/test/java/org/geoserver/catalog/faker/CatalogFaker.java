/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.catalog.faker;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.datafaker.Faker;
import net.datafaker.providers.base.Address;
import org.geoserver.catalog.AttributionInfo;
import org.geoserver.catalog.AuthorityURLInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CoverageDimensionInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataLinkInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.DimensionDefaultValueSetting;
import org.geoserver.catalog.DimensionDefaultValueSetting.Strategy;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.DimensionPresentation;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.Keyword;
import org.geoserver.catalog.KeywordInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.LegendInfo;
import org.geoserver.catalog.MetadataLinkInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WMTSLayerInfo;
import org.geoserver.catalog.WMTSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.AttributionInfoImpl;
import org.geoserver.catalog.impl.AuthorityURL;
import org.geoserver.catalog.impl.CoverageDimensionImpl;
import org.geoserver.catalog.impl.DataLinkInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.DimensionInfoImpl;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.catalog.impl.LayerIdentifier;
import org.geoserver.catalog.impl.LegendInfoImpl;
import org.geoserver.catalog.impl.MetadataLinkInfoImpl;
import org.geoserver.config.ContactInfo;
import org.geoserver.config.CoverageAccessInfo;
import org.geoserver.config.CoverageAccessInfo.QueueType;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.GeoServerInfo.WebUIMode;
import org.geoserver.config.ImageProcessingInfo;
import org.geoserver.config.ImageProcessingInfo.PngEncoderType;
import org.geoserver.config.LoggingInfo;
import org.geoserver.config.ResourceErrorHandling;
import org.geoserver.config.SettingsInfo;
import org.geoserver.config.impl.ContactInfoImpl;
import org.geoserver.config.impl.CoverageAccessInfoImpl;
import org.geoserver.config.impl.GeoServerImpl;
import org.geoserver.config.impl.GeoServerInfoImpl;
import org.geoserver.config.impl.ImageProcessingInfoImpl;
import org.geoserver.config.impl.LoggingInfoImpl;
import org.geoserver.config.impl.ServiceInfoImpl;
import org.geoserver.config.impl.SettingsInfoImpl;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.wfs.GMLInfo;
import org.geoserver.wfs.GMLInfo.SrsNameStyle;
import org.geoserver.wfs.GMLInfoImpl;
import org.geotools.api.coverage.SampleDimensionType;
import org.geotools.api.util.InternationalString;
import org.geotools.util.GrowableInternationalString;
import org.geotools.util.NumberRange;
import org.geotools.util.SimpleInternationalString;
import org.geotools.util.Version;
import org.springframework.util.Assert;

/**
 * A test data factory for GeoServer {@link Catalog} and {@link GeoServer} configuration objects, powered by
 * <a href="https://www.datafaker.net/">DataFaker</a>.
 *
 * <p>Provides a fluent API to create populated objects for testing purposes. The generated data is random yet
 * plausible, thanks to the {@code net.datafaker:datafaker} library.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CatalogFaker faker = new CatalogFaker(getCatalog(), getGeoServer());
 * WorkspaceInfo ws = faker.workspaceInfo();
 * DataStoreInfo ds = faker.dataStoreInfo(ws);
 * FeatureTypeInfo ft = faker.featureTypeInfo(ds);
 * LayerInfo layer = faker.layerInfo(ft, faker.styleInfo());
 * catalog.add(ws);
 * catalog.add(ds);
 * catalog.add(ft);
 * catalog.add(layer);
 * }</pre>
 *
 * <p>The locale of the generated data can be changed using the {@link #withLocale(Locale)}, {@link #italian()}, and
 * {@link #german()} methods.
 *
 * @see Faker
 */
public class CatalogFaker {

    private final Faker faker;
    private Supplier<Catalog> catalog;
    private Supplier<GeoServer> geoserver;

    public CatalogFaker(@Nonnull Catalog catalog) {
        requireNonNull(catalog);
        GeoServerImpl gs = new GeoServerImpl();
        gs.setCatalog(catalog);
        this.catalog = () -> catalog;
        this.geoserver = () -> gs;
        this.faker = new Faker(Locale.ENGLISH);
    }

    public CatalogFaker(@Nonnull Catalog catalog, @Nonnull GeoServer geoserver) {
        this(() -> catalog, () -> geoserver);
    }

    public CatalogFaker(@Nonnull Catalog catalog, @Nonnull GeoServer geoserver, @Nonnull Locale locale) {
        this(() -> catalog, () -> geoserver, locale);
    }

    public CatalogFaker(Supplier<Catalog> catalog, Supplier<GeoServer> geoserver) {
        this(catalog, geoserver, Locale.ENGLISH);
    }

    public CatalogFaker(
            @Nonnull Supplier<Catalog> catalog, @Nonnull Supplier<GeoServer> geoserver, @Nonnull Locale locale) {
        this.catalog = catalog;
        this.geoserver = geoserver;
        this.faker = new Faker(locale);
    }

    /**
     * Returns the {@link Catalog} instance supplied at creation time.
     *
     * @return the catalog.
     */
    public Catalog catalog() {
        return catalog.get();
    }

    /**
     * Returns the {@link Catalog} instance supplied at creation time.
     *
     * @return the catalog.
     */
    public GeoServer geoServer() {
        return geoserver.get();
    }

    private CatalogFactory catalogFactory() {
        return catalog().getFactory();
    }

    /**
     * Exposes the underlying {@link Faker} instance to access its rich API for random data generation.
     *
     * @return the {@link Faker} instance.
     */
    public Faker faker() {
        return faker;
    }

    /**
     * Returns a new {@link CatalogFaker} instance with the locale set to {@link Locale#ITALIAN}.
     *
     * @return a new faker with Italian locale
     */
    public CatalogFaker italian() {
        return withLocale(Locale.ITALIAN);
    }

    /**
     * Returns a new {@link CatalogFaker} instance with the locale set to {@link Locale#GERMAN}.
     *
     * @return a new faker with German locale
     */
    public CatalogFaker german() {
        return withLocale(Locale.GERMAN);
    }

    /**
     * Returns a new {@link CatalogFaker} instance with the specified {@link Locale}.
     *
     * @param locale the locale for the new faker
     * @return a new faker with the given locale
     */
    public CatalogFaker withLocale(@Nonnull Locale locale) {
        return new CatalogFaker(catalog, geoserver, locale);
    }

    /**
     * Creates a new {@link LayerGroupInfo} with a random name in the given workspace.
     *
     * @param workspace the workspace to create the layer group in, can be {@code null}.
     * @return a new {@link LayerGroupInfo}
     */
    public LayerGroupInfo layerGroupInfo(WorkspaceInfo workspace) {
        return layerGroupInfo(id(), workspace, name(), null, null);
    }

    /**
     * Creates a new {@link LayerGroupInfo} with the specified properties.
     *
     * @param id the layer group id
     * @param workspace the workspace, can be {@code null} for a global layer group.
     * @param name the layer group name
     * @param layer an optional layer to add to the group
     * @param style an optional style to add to the group
     * @return a new {@link LayerGroupInfo}
     */
    public LayerGroupInfo layerGroupInfo(
            String id, WorkspaceInfo workspace, String name, PublishedInfo layer, StyleInfo style) {
        // not using factory cause SecuredCatalog would return SecuredLayerGroupInfo which has no id
        // setter
        LayerGroupInfo lg = new LayerGroupInfoImpl();
        OwsUtils.set(lg, "id", id);
        lg.setName(name);
        lg.setWorkspace(workspace);
        if (layer != null) {
            lg.getLayers().add(layer);
        }
        if (style != null) {
            lg.getStyles().add(style);
        }
        OwsUtils.resolveCollections(lg);
        return lg;
    }

    /**
     * Creates a new {@link LayerInfo} for the given resource and default style.
     *
     * @param resource the resource for the layer
     * @param defaultStyle the default style for the layer
     * @return a new {@link LayerInfo}
     */
    public LayerInfo layerInfo(ResourceInfo resource, StyleInfo defaultStyle) {

        return layerInfo(resource.getName() + "-layer-id", resource, resource.getName() + " title", true, defaultStyle);
    }

    /**
     * Creates a new {@link LayerInfo} with the specified properties.
     *
     * @param id the layer id
     * @param resource the resource for the layer
     * @param title the layer title
     * @param enabled whether the layer is enabled
     * @param defaultStyle the default style for the layer
     * @param additionalStyles optional additional styles
     * @return a new {@link LayerInfo}
     */
    public LayerInfo layerInfo(
            String id,
            ResourceInfo resource,
            String title,
            boolean enabled,
            StyleInfo defaultStyle,
            StyleInfo... additionalStyles) {
        LayerInfo lyr = catalogFactory().createLayer();
        OwsUtils.set(lyr, "id", id);
        lyr.setResource(resource);
        lyr.setEnabled(enabled);
        lyr.setDefaultStyle(defaultStyle);
        lyr.setTitle(title);
        for (int i = 0; null != additionalStyles && i < additionalStyles.length; i++) {
            lyr.getStyles().add(additionalStyles[i]);
        }
        OwsUtils.resolveCollections(lyr);
        return lyr;
    }

    /**
     * Creates a new global {@link StyleInfo} with a random name.
     *
     * @return a new {@link StyleInfo}
     */
    public StyleInfo styleInfo() {
        return styleInfo(name());
    }

    /**
     * Creates a new global {@link StyleInfo} with the specified name.
     *
     * @param name the style name
     * @return a new {@link StyleInfo}
     */
    public StyleInfo styleInfo(@Nonnull String name) {
        return styleInfo(name, (WorkspaceInfo) null);
    }

    /**
     * Creates a new {@link StyleInfo} with the specified name and workspace.
     *
     * @param name the style name
     * @param workspace the workspace, can be {@code null} for a global style.
     * @return a new {@link StyleInfo}
     */
    public StyleInfo styleInfo(@Nonnull String name, WorkspaceInfo workspace) {
        return styleInfo(name + "-id", workspace, name, name + ".sld");
    }

    /**
     * Creates a new {@link StyleInfo} with the specified properties.
     *
     * @param id the style id
     * @param workspace the workspace, can be {@code null} for a global style.
     * @param name the style name
     * @param fileName the style file name
     * @return a new {@link StyleInfo}
     */
    public StyleInfo styleInfo(String id, WorkspaceInfo workspace, String name, String fileName) {
        StyleInfo st = catalogFactory().createStyle();
        OwsUtils.set(st, "id", id);
        st.setWorkspace(workspace);
        st.setName(name);
        st.setFilename(fileName);
        OwsUtils.resolveCollections(st);
        return st;
    }

    /**
     * Creates a new {@link WMTSLayerInfo} with the specified properties.
     *
     * @param id the layer id
     * @param store the store for the layer
     * @param namespace the namespace for the layer
     * @param name the layer name
     * @param enabled whether the layer is enabled
     * @return a new {@link WMTSLayerInfo}
     */
    public WMTSLayerInfo wmtsLayerInfo(
            String id, StoreInfo store, NamespaceInfo namespace, String name, boolean enabled) {
        WMTSLayerInfo wmtsl = catalogFactory().createWMTSLayer();
        OwsUtils.set(wmtsl, "id", id);
        wmtsl.setStore(store);
        wmtsl.setNamespace(namespace);
        wmtsl.setName(name);
        wmtsl.setEnabled(enabled);
        OwsUtils.resolveCollections(wmtsl);
        return wmtsl;
    }

    /**
     * Creates a new {@link WMTSStoreInfo} with the specified properties.
     *
     * @param id the store id
     * @param workspace the workspace for the store
     * @param name the store name
     * @param url the capabilities URL
     * @param enabled whether the store is enabled
     * @return a new {@link WMTSStoreInfo}
     */
    public WMTSStoreInfo wmtsStoreInfo(String id, WorkspaceInfo workspace, String name, String url, boolean enabled) {
        WMTSStoreInfo wmtss = catalogFactory().createWebMapTileServer();
        OwsUtils.set(wmtss, "id", id);
        wmtss.setWorkspace(workspace);
        wmtss.setName(name);
        wmtss.setType("WMTS");
        wmtss.setCapabilitiesURL(url);
        wmtss.setEnabled(enabled);
        OwsUtils.resolveCollections(wmtss);
        return wmtss;
    }

    /**
     * Creates a new {@link WMSLayerInfo} with the specified properties.
     *
     * @param id the layer id
     * @param store the store for the layer
     * @param namespace the namespace for the layer
     * @param name the layer name
     * @param enabled whether the layer is enabled
     * @return a new {@link WMSLayerInfo}
     */
    public WMSLayerInfo wmsLayerInfo(
            String id, StoreInfo store, NamespaceInfo namespace, String name, boolean enabled) {
        WMSLayerInfo wmsl = catalogFactory().createWMSLayer();
        OwsUtils.set(wmsl, "id", id);
        wmsl.setStore(store);
        wmsl.setNamespace(namespace);
        wmsl.setName(name);
        wmsl.setEnabled(enabled);
        OwsUtils.resolveCollections(wmsl);
        return wmsl;
    }

    /**
     * Creates a new {@link WMSStoreInfo} with the specified properties.
     *
     * @param id the store id
     * @param wspace the workspace for the store
     * @param name the store name
     * @param url the capabilities URL
     * @param enabled whether the store is enabled
     * @return a new {@link WMSStoreInfo}
     */
    public WMSStoreInfo wmsStoreInfo(String id, WorkspaceInfo wspace, String name, String url, boolean enabled) {
        WMSStoreInfo wms = catalogFactory().createWebMapServer();
        OwsUtils.set(wms, "id", id);
        wms.setName(name);
        wms.setType("WMS");
        wms.setCapabilitiesURL(url);
        wms.setWorkspace(wspace);
        wms.setEnabled(enabled);
        OwsUtils.resolveCollections(wms);
        return wms;
    }

    /**
     * Creates a new {@link CoverageInfo} with the specified properties.
     *
     * @param id the coverage id
     * @param cstore the coverage store
     * @param name the coverage name
     * @return a new {@link CoverageInfo}
     */
    public CoverageInfo coverageInfo(String id, CoverageStoreInfo cstore, String name) {
        CoverageInfo coverage = catalogFactory().createCoverage();
        OwsUtils.set(coverage, "id", id);
        coverage.setName(name);
        coverage.setStore(cstore);
        OwsUtils.resolveCollections(coverage);
        return coverage;
    }

    /**
     * Creates a new {@link CoverageStoreInfo} with the specified properties.
     *
     * @param id the store id
     * @param ws the workspace for the store
     * @param name the store name
     * @param coverageType the coverage type
     * @param uri the coverage URI
     * @return a new {@link CoverageStoreInfo}
     */
    public CoverageStoreInfo coverageStoreInfo(
            String id, WorkspaceInfo ws, String name, String coverageType, String uri) {
        CoverageStoreInfo cstore = catalogFactory().createCoverageStore();
        OwsUtils.set(cstore, "id", id);
        cstore.setName(name);
        cstore.setType(coverageType);
        cstore.setURL(uri);
        cstore.setWorkspace(ws);
        OwsUtils.resolveCollections(cstore);
        return cstore;
    }

    /**
     * Creates a new {@link FeatureTypeInfo} with a random name in the given data store.
     *
     * @param ds the data store
     * @return a new {@link FeatureTypeInfo}
     */
    public FeatureTypeInfo featureTypeInfo(DataStoreInfo ds) {
        String name = name();
        return featureTypeInfo(ds, name);
    }

    /**
     * Creates a new {@link FeatureTypeInfo} with the specified name in the given data store.
     *
     * @param ds the data store
     * @param name the feature type name
     * @return a new {@link FeatureTypeInfo}
     */
    public FeatureTypeInfo featureTypeInfo(DataStoreInfo ds, String name) {
        String prefix = ds.getWorkspace().getName();
        NamespaceInfo ns = catalog().getNamespaceByPrefix(prefix);
        Objects.requireNonNull(ns, "Namespace " + prefix + " does not exist");

        String id = "FeatureType." + name + "." + id();
        String abstracT = faker().company().bs();
        String description = faker().company().buzzword();
        boolean enabled = true;
        return featureTypeInfo(id, ds, ns, name, abstracT, description, enabled);
    }

    /**
     * Creates a new {@link FeatureTypeInfo} with the specified properties.
     *
     * @param id the feature type id
     * @param ds the data store
     * @param ns the namespace
     * @param name the feature type name
     * @param ftAbstract the feature type abstract
     * @param ftDescription the feature type description
     * @param enabled whether the feature type is enabled
     * @return a new {@link FeatureTypeInfo}
     */
    public FeatureTypeInfo featureTypeInfo(
            String id,
            DataStoreInfo ds,
            NamespaceInfo ns,
            String name,
            String ftAbstract,
            String ftDescription,
            boolean enabled) {
        FeatureTypeInfo fttype = catalogFactory().createFeatureType();
        OwsUtils.set(fttype, "id", id);
        fttype.setEnabled(enabled);
        fttype.setName(name);
        fttype.setAbstract(ftAbstract);
        fttype.setDescription(ftDescription);
        fttype.setStore(ds);
        fttype.setNamespace(ns);
        OwsUtils.resolveCollections(fttype);
        return fttype;
    }

    /**
     * Creates a new {@link DataStoreInfo} with a random name in the given workspace.
     *
     * @param ws the workspace
     * @return a new {@link DataStoreInfo}
     */
    public DataStoreInfo dataStoreInfo(WorkspaceInfo ws) {
        return dataStoreInfo(name(), ws);
    }

    /**
     * Creates a new {@link DataStoreInfo} with the specified name in the given workspace.
     *
     * @param name the data store name
     * @param ws the workspace
     * @return a new {@link DataStoreInfo}
     */
    public DataStoreInfo dataStoreInfo(String name, WorkspaceInfo ws) {
        return dataStoreInfo("DataStoreInfo." + id(), ws, name, name + " description", true);
    }

    /**
     * Creates a new {@link DataStoreInfo} with the specified properties.
     *
     * @param id the data store id
     * @param ws the workspace
     * @param name the data store name
     * @param description the data store description
     * @param enabled whether the data store is enabled
     * @return a new {@link DataStoreInfo}
     */
    public DataStoreInfo dataStoreInfo(String id, WorkspaceInfo ws, String name, String description, boolean enabled) {
        DataStoreInfoImpl dstore = (DataStoreInfoImpl) catalogFactory().createDataStore();
        OwsUtils.set(dstore, "id", id);
        dstore.setEnabled(enabled);
        dstore.setName(name);
        dstore.setDescription(description);
        dstore.setWorkspace(ws);
        dstore.setConnectionParameters(new HashMap<>());
        // note: using only string param values to avoid assertEquals() failures due to
        // serialization/deserialization losing type of parameter values
        dstore.getConnectionParameters().put("param1", "test value");
        dstore.getConnectionParameters().put("param2", "1000");
        OwsUtils.resolveCollections(dstore);
        return dstore;
    }

    /**
     * Creates a new {@link WorkspaceInfo} with a random name.
     *
     * @return a new {@link WorkspaceInfo}
     */
    public WorkspaceInfo workspaceInfo() {
        return workspaceInfo(name());
    }

    /**
     * Generates a random name suitable for catalog objects.
     *
     * @return a random name
     */
    public String name() {
        return faker.internet().domainName() + "_" + faker.random().hex();
    }

    /**
     * Creates a new {@link WorkspaceInfo} with the specified name.
     *
     * @param name the workspace name
     * @return a new {@link WorkspaceInfo}
     */
    public WorkspaceInfo workspaceInfo(String name) {
        return workspaceInfo("WorkspaceInfo." + id(), name);
    }

    /**
     * Creates a new {@link WorkspaceInfo} with the specified id and name.
     *
     * @param id the workspace id
     * @param name the workspace name
     * @return a new {@link WorkspaceInfo}
     */
    public WorkspaceInfo workspaceInfo(String id, String name) {
        WorkspaceInfo workspace = catalogFactory().createWorkspace();
        OwsUtils.set(workspace, "id", id);
        workspace.setName(name);
        OwsUtils.resolveCollections(workspace);
        return workspace;
    }

    /**
     * Generates a random URL.
     *
     * @return a random URL
     */
    public String url() {
        return faker().internet().url() + "/" + faker.random().hex();
    }

    private String id() {
        return faker().idNumber().valid();
    }

    /**
     * Creates a new {@link NamespaceInfo} with a random prefix and URI.
     *
     * @return a new {@link NamespaceInfo}
     */
    public NamespaceInfo namespaceInfo() {
        return namespaceInfo(id(), faker().letterify("ns-????"), url());
    }

    /**
     * Creates a new {@link NamespaceInfo} with the specified prefix and a random URI.
     *
     * @param name the namespace prefix
     * @return a new {@link NamespaceInfo}
     */
    public NamespaceInfo namespaceInfo(String name) {
        return namespaceInfo(id(), name, url());
    }

    public NamespaceInfo namespaceInfo(WorkspaceInfo ws) {
        return namespaceInfo(id(), ws.getName(), url());
    }

    /**
     * Creates a new {@link NamespaceInfo} with the specified id, prefix, and URI.
     *
     * @param id the namespace id
     * @param name the namespace prefix
     * @param uri the namespace URI
     * @return a new {@link NamespaceInfo}
     */
    public NamespaceInfo namespaceInfo(String id, String name, String uri) {
        NamespaceInfo namespace = catalogFactory().createNamespace();
        OwsUtils.set(namespace, "id", id);
        namespace.setPrefix(name);
        namespace.setURI(uri);
        OwsUtils.resolveCollections(namespace);
        return namespace;
    }

    /**
     * Creates a new {@link GeoServerInfo} with plausible default values.
     *
     * @return a new {@link GeoServerInfo}
     */
    public GeoServerInfo geoServerInfo() {
        GeoServerInfoImpl g = new GeoServerInfoImpl();

        g.setId("GeoServer.global");
        g.setAdminPassword("geoserver");
        g.setAdminUsername("admin");
        g.setAllowStoredQueriesPerWorkspace(true);
        g.setCoverageAccess(createCoverageAccessInfo());
        g.setFeatureTypeCacheSize(1000);
        g.setGlobalServices(true);
        g.setId("GeoServer.global");
        g.setImageProcessing(imageProcessingInfo());
        // don't set lock provider with g.setLockProviderName("testLockProvider") to avoid a warning
        // stack trace that the bean does not exist
        g.setMetadata(metadataMap("k1", Integer.valueOf(1), "k2", "2", "k3", Boolean.FALSE));
        g.setResourceErrorHandling(ResourceErrorHandling.OGC_EXCEPTION_REPORT);
        g.setSettings(settingsInfo(null));
        g.setUpdateSequence(faker().random().nextLong(1000L));
        g.setWebUIMode(WebUIMode.DO_NOT_REDIRECT);
        g.setXmlExternalEntitiesEnabled(Boolean.TRUE);
        g.setXmlPostRequestLogBufferSize(1024);

        return g;
    }

    /**
     * Creates a new {@link MetadataMap} with the given key-value pairs.
     *
     * @param kvps a list of key-value pairs, must be an even number of arguments.
     * @return a new {@link MetadataMap}
     */
    public MetadataMap metadataMap(Serializable... kvps) {
        Assert.isTrue(kvps == null || kvps.length % 2 == 0, "expected even number");
        MetadataMap m = new MetadataMap();
        if (kvps != null) {
            for (int i = 0; i < kvps.length; i += 2) {
                m.put((String) kvps[i], kvps[i + 1]);
            }
        }
        return m;
    }

    /**
     * Creates a new {@link ImageProcessingInfo} with plausible default values.
     *
     * @return a new {@link ImageProcessingInfo}
     */
    public ImageProcessingInfo imageProcessingInfo() {
        ImageProcessingInfoImpl jai = new ImageProcessingInfoImpl();
        jai.setAllowInterpolation(true);
        jai.setMemoryCapacity(4096);
        jai.setMemoryThreshold(0.75);
        jai.setPngEncoderType(PngEncoderType.PNGJ);
        jai.setRecycling(true);
        jai.setTilePriority(1);
        jai.setTileThreads(7);
        return jai;
    }

    private CoverageAccessInfo createCoverageAccessInfo() {
        CoverageAccessInfoImpl c = new CoverageAccessInfoImpl();
        c.setCorePoolSize(9);
        c.setImageIOCacheThreshold(11);
        c.setKeepAliveTime(1000);
        c.setMaxPoolSize(18);
        c.setQueueType(QueueType.UNBOUNDED);
        return c;
    }

    /**
     * Creates a new {@link ContactInfo} with random address and contact details.
     *
     * @return a new {@link ContactInfo}
     */
    public ContactInfo contactInfo() {
        ContactInfoImpl c = new ContactInfoImpl();

        Address fakeAddress = faker.address();

        c.setId(faker.idNumber().valid());
        c.setAddress(fakeAddress.fullAddress());
        c.setAddressCity(fakeAddress.city());
        c.setAddressCountry(fakeAddress.country());
        c.setAddressDeliveryPoint(fakeAddress.secondaryAddress());
        c.setAddressPostalCode(fakeAddress.zipCode());
        c.setAddressState(fakeAddress.state());

        c.setContactFacsimile(faker.phoneNumber().phoneNumber());
        c.setContactOrganization(faker.company().name());
        c.setContactPerson(faker.name().fullName());
        c.setContactVoice(faker.phoneNumber().cellPhone());
        c.setOnlineResource(faker.internet().url());

        Address it = italian().faker().address();
        Address de = german().faker().address();
        c.setInternationalAddress(
                internationalString(Locale.ITALIAN, it.fullAddress(), Locale.GERMAN, de.fullAddress()));
        return c;
    }

    /**
     * Creates a new {@link LoggingInfo} with plausible default values.
     *
     * @return a new {@link LoggingInfo}
     */
    public LoggingInfo loggingInfo() {
        LoggingInfoImpl l = new LoggingInfoImpl();
        l.setId("weird-this-has-id");
        l.setLevel("super");
        l.setLocation("there");
        l.setStdOutLogging(true);
        return l;
    }

    /**
     * Creates a new {@link SettingsInfo} for the given workspace.
     *
     * @param workspace the workspace, can be {@code null} for global settings.
     * @return a new {@link SettingsInfo}
     */
    public SettingsInfo settingsInfo(WorkspaceInfo workspace) {
        SettingsInfo s = new SettingsInfoImpl();
        s.setWorkspace(workspace);
        String id = workspace == null ? "global-settings-id" : workspace.getName() + "-settings-id";
        OwsUtils.set(s, "id", id);

        s.setTitle(workspace == null ? "Global Settings" : workspace.getName() + " Settings");
        s.setCharset("UTF-8");
        s.setContact(contactInfo());
        s.getMetadata().putAll(metadataMap("k1", Integer.valueOf(1), "k2", "2", "k3", Boolean.FALSE));
        s.setNumDecimals(9);
        s.setOnlineResource("http://geoserver.org");
        s.setProxyBaseUrl("http://test.geoserver.org");
        s.setSchemaBaseUrl("file:data/schemas");
        s.setVerbose(true);
        s.setVerboseExceptions(true);
        return s;
    }

    /**
     * Creates a new {@link ServiceInfoImpl} with the given name and factory.
     *
     * @param name the service name
     * @param factory a supplier for the service info implementation
     * @return a new {@link ServiceInfoImpl}
     */
    public <S extends ServiceInfoImpl> S serviceInfo(String name, Supplier<S> factory) {
        S s = factory.get();
        s.setId(name + "-id");
        s.setName(name);
        s.setTitle(name + " Title");
        s.setAbstract(name + " Abstract");
        s.setInternationalTitle(internationalString(
                Locale.ENGLISH, name + " english title", Locale.CANADA_FRENCH, name + "titre anglais"));
        s.setInternationalAbstract(internationalString(
                Locale.ENGLISH, name + " english abstract", Locale.CANADA_FRENCH, name + "résumé anglais"));
        s.setAccessConstraints("NONE");
        s.setCiteCompliant(true);
        s.setEnabled(true);
        s.setExceptionFormats(Collections.singletonList("fake-" + name + "-exception-format"));
        s.setFees("NONE");
        s.setKeywords(Lists.newArrayList(keywordInfo(), keywordInfo()));
        s.setMaintainer("Claudious whatever");
        s.setMetadata(metadataMap(name, "something"));
        MetadataLinkInfoImpl metadataLink = new MetadataLinkInfoImpl();
        metadataLink.setAbout("about");
        metadataLink.setContent("content");
        metadataLink.setId("medatata-link-" + name);
        metadataLink.setMetadataType("fake");
        metadataLink.setType("void");
        s.setMetadataLink(metadataLink);
        s.setOnlineResource("http://geoserver.org/" + name);
        s.setOutputStrategy("SPEED");
        s.setSchemaBaseURL("file:data/" + name);
        s.setVerbose(true);
        List<Version> versions = Lists.newArrayList(new Version("1.0.0"), new Version("2.0.0"));
        s.getVersions().addAll(versions);
        return s;
    }

    /**
     * Creates a new {@link KeywordInfo} with a random value.
     *
     * @return a new {@link KeywordInfo}
     */
    public KeywordInfo keywordInfo() {
        Keyword k1 = new Keyword(faker().chuckNorris().fact());
        k1.setLanguage("eng");
        k1.setVocabulary("watchit");
        return k1;
    }

    /**
     * Creates a new {@link AuthorityURLInfo} with a random name and URL.
     *
     * @return a new {@link AuthorityURLInfo}
     */
    public AuthorityURLInfo authorityURLInfo() {
        AuthorityURL a1 = new AuthorityURL();
        a1.setHref(faker().internet().url());
        a1.setName(faker().numerify("test-auth-url-####"));
        return a1;
    }

    /**
     * Creates a list of {@link AuthorityURLInfo}s.
     *
     * @param count the number of authority URLs to create
     * @return a list of {@link AuthorityURLInfo}s
     */
    public List<AuthorityURLInfo> authUrls(int count) {
        return IntStream.range(0, count).mapToObj(i -> this.authorityURLInfo()).toList();
    }

    /**
     * Creates a new {@link InternationalString} with the given value.
     *
     * @param val the string value
     * @return a new {@link InternationalString}
     */
    public InternationalString internationalString(String val) {
        return new SimpleInternationalString(val);
    }

    /**
     * Creates a new {@link GrowableInternationalString} with the given value for the given locale.
     *
     * @param l the locale
     * @param val the string value
     * @return a new {@link GrowableInternationalString}
     */
    public GrowableInternationalString internationalString(Locale l, String val) {
        GrowableInternationalString s = new GrowableInternationalString();
        s.add(l, val);
        return s;
    }

    /**
     * Creates a new {@link GrowableInternationalString} with the given values for the given locales.
     *
     * @param l1 the first locale
     * @param val1 the first string value
     * @param l2 the second locale
     * @param val2 the second string value
     * @return a new {@link GrowableInternationalString}
     */
    public GrowableInternationalString internationalString(Locale l1, String val1, Locale l2, String val2) {
        GrowableInternationalString s = new GrowableInternationalString();
        s.add(l1, val1);
        s.add(l2, val2);
        return s;
    }

    /**
     * Creates a new {@link AttributionInfo} with random values.
     *
     * @return a new {@link AttributionInfo}
     */
    public AttributionInfo attributionInfo() {
        AttributionInfoImpl attinfo = new AttributionInfoImpl();
        attinfo.setId(faker.idNumber().valid());
        attinfo.setHref(faker.internet().url());
        attinfo.setLogoWidth(faker.number().numberBetween(128, 512));
        attinfo.setLogoHeight(faker.number().numberBetween(128, 512));
        attinfo.setTitle(faker.company().bs());
        return attinfo;
    }

    /**
     * Creates a new {@link MetadataLinkInfo} with random values.
     *
     * @return a new {@link MetadataLinkInfo}
     */
    public MetadataLinkInfo metadataLink() {
        MetadataLinkInfoImpl link = new MetadataLinkInfoImpl();
        link.setId(faker().idNumber().valid());
        link.setAbout(faker().company().buzzword());
        link.setContent(faker().internet().url());
        link.setMetadataType("metadataType");
        link.setType("type");
        return link;
    }

    /**
     * Creates a new {@link CoverageDimensionInfo} with random values.
     *
     * @return a new {@link CoverageDimensionInfo}
     */
    public CoverageDimensionInfo coverageDimensionInfo() {
        CoverageDimensionImpl c = new CoverageDimensionImpl();
        c.setDescription(faker().company().bs());
        c.setDimensionType(SampleDimensionType.UNSIGNED_1BIT);
        c.setId(faker.idNumber().valid());
        c.setName(faker.internet().domainName());
        c.setNullValues(Lists.newArrayList(0.0));
        c.setRange(NumberRange.create(0.0, 255.0));
        c.setUnit("unit");
        return c;
    }

    /**
     * Creates a new {@link DimensionInfo} with random values.
     *
     * @return a new {@link DimensionInfo}
     */
    public DimensionInfo dimensionInfo() {
        DimensionInfoImpl di = new DimensionInfoImpl();
        di.setAcceptableInterval("searchRange");
        di.setAttribute("attribute");
        DimensionDefaultValueSetting defaultValue = new DimensionDefaultValueSetting();
        defaultValue.setReferenceValue("referenceValue");
        defaultValue.setStrategyType(Strategy.MAXIMUM);
        di.setDefaultValue(defaultValue);
        di.setEnabled(faker.bool().bool());
        di.setNearestMatchEnabled(faker.bool().bool());
        di.setResolution(BigDecimal.valueOf(faker.number().randomDouble(4, 0, 1000)));
        di.setUnits("metre");
        di.setUnitSymbol("m");
        di.setPresentation(DimensionPresentation.DISCRETE_INTERVAL);
        return di;
    }

    /**
     * Creates a new {@link DataLinkInfo} with random values.
     *
     * @return a new {@link DataLinkInfo}
     */
    public DataLinkInfo dataLinkInfo() {
        DataLinkInfoImpl dl = new DataLinkInfoImpl();
        dl.setAbout(faker.yoda().quote());
        dl.setContent(faker.internet().url());
        dl.setId(faker.idNumber().valid());
        dl.setType(faker.internet().domainWord());
        return dl;
    }

    /**
     * Creates a new {@link LayerIdentifier} with random values.
     *
     * @return a new {@link LayerIdentifier}
     */
    public LayerIdentifier layerIdentifierInfo() {
        org.geoserver.catalog.impl.LayerIdentifier li = new LayerIdentifier();
        li.setAuthority(faker.internet().url());
        li.setIdentifier(faker.idNumber().valid());
        return li;
    }

    /**
     * Creates a new {@link LegendInfo} with random values.
     *
     * @return a new {@link LegendInfo}
     */
    public LegendInfo legendInfo() {
        LegendInfoImpl l = new LegendInfoImpl();
        l.setFormat("image/png");
        l.setHeight(faker.number().numberBetween(10, 20));
        l.setWidth(faker.number().numberBetween(10, 20));
        l.setOnlineResource(faker.internet().url());
        l.setId(faker.idNumber().valid());
        return l;
    }

    /**
     * Creates a new {@link GMLInfo} with plausible default values.
     *
     * @return a new {@link GMLInfo}
     */
    public GMLInfo gmlInfo() {
        GMLInfo info = new GMLInfoImpl();
        info.setMimeTypeToForce("application/gml;test=true");
        info.setOverrideGMLAttributes(true);
        info.setSrsNameStyle(SrsNameStyle.URN);
        return info;
    }
}

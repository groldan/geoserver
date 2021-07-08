/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.util.Converters;

/**
 * A store of geoaspatial resources.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface StoreInfo extends CatalogInfo {

    /** The catalog the store is part of. */
    Catalog getCatalog();

    /**
     * The store name.
     *
     * <p>This value is unique among all stores and can be used to identify the store.
     *
     * @uml.property name="name"
     */
    String getName();

    /**
     * Sets the name of the store.
     *
     * @uml.property name="name"
     */
    void setName(String name);

    /**
     * The store description.
     *
     * <p>This is usually something that is used in a user interface.
     *
     * @uml.property name="description"
     */
    String getDescription(); // FIXME: InternationalString ?

    /**
     * Sets the store description.
     *
     * <p>This is usually something that is used in a user interface.
     *
     * @uml.property name="description"
     */
    void setDescription(String description);

    /**
     * The store type.
     *
     * <p>This value is a well known string representing the nature of the store. Examples include
     * "Shapefile", "Postgis", "GeoTIFF", etc...
     */
    String getType();

    /** Sets the type of the store. */
    void setType(String type);

    /**
     * Flag indicating wether or not teh store is enabled or not.
     *
     * @uml.property name="enabled"
     */
    boolean isEnabled();

    /**
     * Sets the store enabled / disabled flag.
     *
     * @uml.property name="enabled"
     */
    void setEnabled(boolean enabled);

    /**
     * The namespace the store is part of.
     *
     * <p>This value is often used to set the namespace of {@link ResourceInfo}objects which are
     * associated to the store.
     *
     * @uml.property name="namespace"
     * @uml.associationEnd inverse="storeInfo:org.geoserver.catalog.NamespaceInfo"
     */
    // NamespaceInfo getNamespace();

    /**
     * Sets the namespace the store is part of.
     *
     * @uml.property name="namespace"
     */
    // void setNamespace(NamespaceInfo namespace);

    /** The workspace the store is part of. */
    WorkspaceInfo getWorkspace();

    /** Sets the workspace the store is part of. */
    void setWorkspace(WorkspaceInfo workspace);

    /**
     * The map of connection paramters specific to the store.
     *
     * <p>Key values in this map are of type {@link String}, and values are of type {@link
     * Serializable}.
     *
     * @uml.property name="connectionParameters"
     */
    Map<String, Serializable> getConnectionParameters();

    /**
     * An error associated with the store.
     *
     * <p>This value is used to store a problem that occured while attemping to connect to the
     * underlying resource of the store. It returns <code>null</code> if no such error exists.
     *
     * <p>This is a transient property of the store.
     */
    Throwable getError();

    /**
     * Associates an error with the store.
     *
     * @see #getError()
     */
    void setError(Throwable t);

    /**
     * Creates an adapter for the store.
     *
     * <p>
     *
     * @param adapterClass The class of the adapter.
     * @param hints Hints to use when creating the adapter.
     * @return The adapter, an intsanceof adapterClass, or <code>null</code>.
     */
    <T extends Object> T getAdapter(Class<T> adapterClass, Map<?, ?> hints);

    /**
     * @return Returns a resource with the specified name that is provided by the store, or <code>
     *     null</code> if no such resource exists.
     *     <p>The monitor is used to report the progress of loading resoures and report any warnings
     *     / errors that occur in doing so. Monitor may also be null.
     */
    // <T extends Resource> T getResource( String name, ProgressListener monitor)
    //    throws IOException;

    /**
     * @return Returns the resources provided by this store.
     *     <p>The monitor is used to report the progress of loading resoures and report any warnings
     *     / errors that occur in doing so. Monitor may also be null.
     * @uml.property name="resources"
     * @uml.associationEnd multiplicity="(0 -1)" inverse="storeInfo:org.geoserver.catalog.Resource"
     */
    // <T extends Resource> Iterator<T> getResources(ProgressListener monitor) throws IOException;

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code StoreInfo} based on the
     * interface accessors
     */
    public static int hashCode(StoreInfo o) {
        final int prime = 31;
        return prime * CatalogInfo.hashCode(o)
                + Objects.hash(
                        o.getDescription(),
                        o.isEnabled(),
                        o.getName(),
                        o.getType(),
                        o.getConnectionParameters(),
                        o.getWorkspace());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code StoreInfo} and another
     * object based on the interface accessors
     */
    public static boolean equals(StoreInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof StoreInfo)) return false;
        StoreInfo other = (StoreInfo) obj;
        return CatalogInfo.equals(o, other)
                && o.isEnabled() == other.isEnabled()
                && Objects.equals(o.getName(), other.getName())
                && Objects.equals(o.getType(), other.getType())
                && Objects.equals(o.getDescription(), other.getDescription())
                && Objects.equals(o.getWorkspace(), other.getWorkspace())
                && connectionParametersEquals(
                        o.getConnectionParameters(), other.getConnectionParameters());
    }

    /**
     * Accounts for the fact that parameter values might be equivalent though of different types,
     * and {@link Param#lookUp(Map) DataAccessFactory$Param#lookUp(Map)} will perform the type
     * conversion. E.g., a {@code boolean} parameter might have a {@code Boolean} or {@code String}
     * value in the map.
     */
    static boolean connectionParametersEquals(Map<String, ?> params1, Map<String, ?> params2) {
        if (params1 == params2) return true;
        if (params1 == null || params2 == null) return false;
        if (!params1.keySet().equals(params2.keySet())) return false;
        for (Map.Entry<String, ?> e1 : params1.entrySet()) {
            Object v1 = e1.getValue();
            Object v2 = params2.get(e1.getKey());
            if (!Objects.equals(v1, v2)) {
                // values differ, try cannonicalizing them as strings
                String s1 = Converters.convert(v1, String.class);
                String s2 = Converters.convert(v2, String.class);
                if ((v1 != null && s1 == null)
                        || (v2 != null && s2 == null)
                        || !Objects.equals(s1, s2)) {
                    return false;
                }
            }
        }
        return true;
    }
}

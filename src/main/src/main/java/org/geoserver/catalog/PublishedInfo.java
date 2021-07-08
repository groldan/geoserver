/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.List;
import java.util.Objects;
import org.opengis.util.InternationalString;

/**
 * Interface for publishable entities contained in a Layer Group.
 *
 * @author Davide Savazzi - geo-solutions.it
 */
public interface PublishedInfo extends CatalogInfo {

    /** Use the entity as capabilities root element if it's the only top level element */
    public static final String ROOT_IN_CAPABILITIES = "rootInCapabilities";

    /** Returns the name. */
    String getName();

    /** Sets the name. */
    void setName(String name);

    /**
     * The derived prefixed name.
     *
     * <p>If a workspace is set this method returns:
     *
     * <pre>
     *   getWorkspace().getName() + ":" + getName();
     * </pre>
     *
     * Otherwise it simply returns:
     *
     * <pre>getName()</pre>
     */
    String prefixedName();

    /** Returns the title. */
    String getTitle();

    /** Sets the title. */
    void setTitle(String title);

    /** Returns the internationalTitle. */
    InternationalString getInternationalTitle();

    /** Sets the internationalTitle. */
    void setInternationalTitle(InternationalString internationalTitle);

    /** Returns the abstract. */
    String getAbstract();

    /** Sets the abstract. */
    void setAbstract(String abstractTxt);

    /** Returns the internationalAbstract. */
    InternationalString getInternationalAbstract();

    /** Sets the internationalAbstract. */
    void setInternationalAbstract(InternationalString internationalAbstract);

    /** Returns the list of authority URLs */
    List<AuthorityURLInfo> getAuthorityURLs();

    /** Returns the list of identifiers */
    List<LayerIdentifierInfo> getIdentifiers();

    /** The type of the layer. */
    PublishedType getType();

    /**
     * Gets the attribution information for this layer.
     *
     * @return an AttributionInfo instance with the layer's attribution information.
     * @see AttributionInfo
     */
    AttributionInfo getAttribution();

    /**
     * Sets the attribution information for this layer.
     *
     * @param attribution an AttributionInfo instance with the new attribution information.
     * @see AttributionInfo
     */
    void setAttribution(AttributionInfo attribution);

    /**
     * Flag indicating wether the layer is enabled or not.
     *
     * @uml.property name="enabled"
     */
    boolean isEnabled();

    /**
     * Sets the flag indicating wether the layer is enabled or not.
     *
     * @uml.property name="enabled"
     */
    void setEnabled(boolean enabled);

    /**
     * Returns true if the layer existence should be advertised (true by default, unless otherwise
     * set)
     */
    boolean isAdvertised();

    /** Set to true if the layer should be advertised, false otherwise */
    void setAdvertised(boolean advertised);

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@link PublishedInfo} based on the
     * interface accessors
     */
    public static int hashCode(PublishedInfo o) {
        final int prime = 31;
        return prime * CatalogInfo.hashCode(o)
                + Objects.hash(o.getAttribution(), o.getAuthorityURLs(), o.getIdentifiers());
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@link PublishedInfo} and
     * another object based on the interface accessors
     */
    public static boolean equals(PublishedInfo o, Object obj) {
        if (o == obj) return true;
        if (!(obj instanceof PublishedInfo)) return false;
        PublishedInfo other = (PublishedInfo) obj;
        return CatalogInfo.equals(o, obj)
                && Objects.equals(o.getAttribution(), other.getAttribution())
                && Objects.equals(o.getAuthorityURLs(), other.getAuthorityURLs())
                && Objects.equals(o.getIdentifiers(), other.getIdentifiers());
    }
}

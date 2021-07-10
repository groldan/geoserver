/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.geotools.geometry.jts.ReferencedEnvelope;

/**
 * A map in which the layers grouped together can be referenced as a regular layer.
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface LayerGroupInfo extends PublishedInfo {

    /** Enumeration for mode of layer group. */
    public enum Mode {
        /**
         * The layer group is seen as a single exposed layer with a name, does not actually contain
         * the layers it's referencing
         */
        SINGLE {
            @Override
            public String getName() {
                return "Single";
            }

            @Override
            public Integer getCode() {
                return 0;
            }
        },
        /**
         * The layer group is seen as a single exposed layer with a name, but contains the layers
         * it's referencing, thus hiding them from the caps document unless also shown in other tree
         * mode layers
         */
        OPAQUE_CONTAINER {
            @Override
            public String getName() {
                return "Opaque Container";
            }

            @Override
            public Integer getCode() {
                // added last, but a cross in between SINGLE and NAMED semantically,
                // so added in this position
                return 4;
            }
        },
        /**
         * The layer group retains a Name in the layer tree, but also exposes its nested layers in
         * the capabilities document.
         */
        NAMED {
            @Override
            public String getName() {
                return "Named Tree";
            }

            @Override
            public Integer getCode() {
                return 1;
            }
        },
        /**
         * The layer group is exposed in the tree, but does not have a Name element, showing
         * structure but making it impossible to get all the layers at once.
         */
        CONTAINER {
            @Override
            public String getName() {
                return "Container Tree";
            }

            @Override
            public Integer getCode() {
                return 2;
            }
        },
        /** A special mode created to manage the earth observation requirements. */
        EO {
            @Override
            public String getName() {
                return "Earth Observation Tree";
            }

            @Override
            public Integer getCode() {
                return 3;
            }
        };

        public abstract String getName();

        public abstract Integer getCode();
    }

    /** Layer group mode. */
    Mode getMode();

    /** Sets layer group mode. */
    void setMode(Mode mode);

    /**
     * Get whether the layer group is forced to be not queryable and hence can not be subject of a
     * GetFeatureInfo request.
     *
     * <p>In order to preserve current default behavior (A LayerGroup is queryable when at least a
     * child layer is queryable), this flag allows explicitly indicate that it is not queryable
     * independently how the child layers are configured.
     *
     * <p>Default is {@code false}
     */
    boolean isQueryDisabled();

    /**
     * Set the layer group to be not queryable and hence can not be subject of a GetFeatureInfo
     * request.
     */
    void setQueryDisabled(boolean queryDisabled);

    /** Returns a workspace or <code>null</code> if global. */
    WorkspaceInfo getWorkspace();

    /** Get root layer. */
    LayerInfo getRootLayer();

    /** Set root layer. */
    void setRootLayer(LayerInfo rootLayer);

    /** Get root layer style. */
    StyleInfo getRootLayerStyle();

    /** Set root layer style. */
    void setRootLayerStyle(StyleInfo style);

    /** The layers and layer groups in the group. */
    List<PublishedInfo> getLayers();

    /**
     * The styles for the layers in the group.
     *
     * <p>This list is a 1-1 correspondence to {@link #getLayers()}.
     */
    List<StyleInfo> getStyles();

    /** */
    List<LayerInfo> layers();

    /** */
    List<StyleInfo> styles();

    /** The bounds for the base map. */
    ReferencedEnvelope getBounds();

    /** Sets the bounds for the base map. */
    void setBounds(ReferencedEnvelope bounds);

    /** Sets the workspace. */
    void setWorkspace(WorkspaceInfo workspace);

    /**
     * A collection of metadata links for the resource.
     *
     * @uml.property name="metadataLinks"
     * @see MetadataLinkInfo
     */
    List<MetadataLinkInfo> getMetadataLinks();

    /**
     * Return the keywords associated with this layer group. If no keywords are available an empty
     * list should be returned.
     *
     * @return a non NULL list containing the keywords associated with this layer group
     */
    default List<KeywordInfo> getKeywords() {
        return new ArrayList<>();
    }

    /**
     * Canonical implementation of {@link Object#hashCode()} for {@code LayerGroupInfo} based on the
     * interface accessors
     *
     * @since 20.0
     */
    public static int hashCode(LayerGroupInfo lg) {
        final int prime = 31;
        int result = PublishedInfo.hashCode(lg);
        result =
                prime * result
                        + Objects.hash(
                                lg.getAbstract(),
                                lg.isAdvertised(),
                                lg.getBounds(),
                                lg.isEnabled(),
                                lg.getInternationalAbstract(),
                                lg.getInternationalTitle(),
                                lg.getKeywords(),
                                lg.layers(),
                                lg.getMetadataLinks(),
                                lg.getMode(),
                                lg.getName(),
                                lg.getLayers(),
                                lg.isQueryDisabled(),
                                lg.getRootLayer(),
                                lg.getRootLayerStyle(),
                                lg.getTitle(),
                                lg.getWorkspace());
        // for consistency with equals()
        List<StyleInfo> styles = canonicalStyles(lg.getStyles(), lg.getLayers());
        result = prime * result + Objects.hash(styles);
        return result;
    }

    /**
     * Canonical implementation of {@link Object#equals(Object)} for a {@code LayerGroupInfo} and
     * another object based on the interface accessors
     *
     * <p>A way to compare two LayerGroupInfo instances that works around all the wrappers we have
     * around (secured, decorating ecc) all changing some aspects of the bean and breaking usage of
     * "common" equality). This method only uses getters to fetch the fields. Could have been build
     * using EqualsBuilder and reflection, but would have been very slow and we do lots of these
     * calls on large catalogs.
     *
     * @since 20.0
     */
    public static boolean equals(LayerGroupInfo lg, Object obj) {
        if (lg == obj) return true;
        if (!PublishedInfo.equals(lg, obj)) return false;
        if (!(obj instanceof LayerGroupInfo)) return false;
        LayerGroupInfo other = (LayerGroupInfo) obj;
        boolean equals =
                lg.getMode() == other.getMode()
                        && Objects.equals(lg.getName(), other.getName())
                        && Objects.equals(lg.isAdvertised(), other.isAdvertised())
                        && Objects.equals(lg.isEnabled(), other.isEnabled())
                        && Objects.equals(lg.isQueryDisabled(), other.isQueryDisabled())
                        && Objects.equals(lg.getAbstract(), other.getAbstract())
                        && Objects.equals(lg.getBounds(), other.getBounds())
                        && Objects.equals(
                                lg.getInternationalAbstract(), other.getInternationalAbstract())
                        && Objects.equals(lg.getInternationalTitle(), other.getInternationalTitle())
                        && Objects.equals(lg.getKeywords(), other.getKeywords())
                        && Objects.equals(lg.getMetadataLinks(), other.getMetadataLinks())
                        && Objects.equals(lg.getLayers(), other.getLayers())
                        && Objects.equals(lg.getRootLayer(), other.getRootLayer())
                        && Objects.equals(lg.getRootLayerStyle(), other.getRootLayerStyle())
                        && Objects.equals(lg.getTitle(), other.getTitle())
                        && Objects.equals(lg.getWorkspace(), other.getWorkspace())
                        && Objects.equals(lg.layers(), other.layers());

        if (equals) {
            List<StyleInfo> styles = canonicalStyles(lg.getStyles(), lg.getLayers());
            List<StyleInfo> otherStyles = canonicalStyles(other.getStyles(), other.getLayers());
            equals = Objects.equals(styles, otherStyles);
        }
        return equals;
    }

    /**
     * Styles, especially when using defaults, can be represented in too many ways (null, list of
     * nulls, and so on). This returns a single canonical representation for those cases, trying not
     * to allocate new objects.
     */
    static List<StyleInfo> canonicalStyles(List<StyleInfo> styles, List<PublishedInfo> layers) {
        if (styles == null || styles.isEmpty()) {
            return null;
        }
        boolean allNull = true;
        for (StyleInfo s : styles) {
            if (s != null) {
                allNull = false;
                break;
            }
        }
        if (allNull) {
            return null;
        }

        // at least one non null element, are they at least aligned with layers?
        if (styles.size() == layers.size()) {
            return styles;
        }

        // not aligned, build a new representation
        List<StyleInfo> canonical = new ArrayList<>(layers.size());
        for (int i = 0; i < layers.size(); i++) {
            StyleInfo s = styles.size() > i ? styles.get(i) : null;
            canonical.add(s);
        }
        return canonical;
    }
}

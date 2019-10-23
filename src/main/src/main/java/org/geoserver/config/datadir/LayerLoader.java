package org.geoserver.config.datadir;

import java.util.function.Consumer;
import java.util.logging.Level;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.config.util.XStreamPersister;

/**
 * Generic layer catalog loader for all types of IO resources
 *
 * @author Andrea Aime - GeoSolutions
 */
final class LayerLoader<T extends ResourceInfo> implements Consumer<LayerContents> {

    Class<T> clazz;
    XStreamPersister xp;
    Catalog catalog;

    public LayerLoader(Class<T> clazz, XStreamPersister xp, Catalog catalog) {
        this.clazz = clazz;
        this.xp = xp;
        this.catalog = catalog;
    }

    @Override
    public void accept(LayerContents lc) {
        T ft = null;
        try {
            ft = DataDirectoryLoader.depersist(xp, lc.contents, clazz);
            catalog.add(ft);
        } catch (Exception e) {
            DataDirectoryLoader.LOGGER.log(Level.WARNING, "Failed to load resource", e);
            return;
        }

        if (DataDirectoryLoader.LOGGER.isLoggable(Level.INFO)) {
            String type =
                    ft instanceof CoverageInfo
                            ? "coverage"
                            : ft instanceof FeatureTypeInfo ? "feature type" : "resource";
            DataDirectoryLoader.LOGGER.info(
                    "Loaded "
                            + type
                            + " '"
                            + lc.resource.name()
                            + "', "
                            + (ft.isEnabled() ? "enabled" : "disabled"));
        }

        try {
            LayerInfo l = DataDirectoryLoader.depersist(xp, lc.layerContents, LayerInfo.class);
            catalog.add(l);

            DataDirectoryLoader.LOGGER.info("Loaded layer '" + l.getName() + "'");
        } catch (Exception e) {
            DataDirectoryLoader.LOGGER.log(
                    Level.WARNING, "Failed to load layer " + lc.resource.name(), e);
        }
    }
}

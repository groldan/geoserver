package org.geoserver.config.datadir;

import java.io.IOException;
import java.util.logging.Logger;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;
import org.geotools.util.logging.Logging;

/** {@link ResourceMapper} for workspaces */
final class StoreMapper implements ResourceMapper<StoreContents> {
    static Logger LOGGER = Logging.getLogger("org.geoserver");

    @Override
    public StoreContents apply(Resource sd) throws IOException {
        Resource f = sd.get("datastore.xml");
        if (Resources.exists(f)) {
            return new StoreContents(f, f.getContents());
        }
        f = sd.get("coveragestore.xml");
        if (Resources.exists(f)) {
            return new StoreContents(f, f.getContents());
        }
        f = sd.get("wmsstore.xml");
        if (Resources.exists(f)) {
            return new StoreContents(f, f.getContents());
        }
        f = sd.get("wmtsstore.xml");
        if (Resources.exists(f)) {
            return new StoreContents(f, f.getContents());
        }
        if (!DataDirectoryLoader.isConfigDirectory(sd)) {
            LOGGER.warning("Ignoring store directory '" + sd.name() + "'");
        }
        // nothing found
        return null;
    }
}

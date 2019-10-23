package org.geoserver.config.datadir;

import java.io.IOException;
import java.util.logging.Level;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;

/** Resource/Layer mapper to IO resources (generic) */
final class ResourceLayerMapper implements ResourceMapper<LayerContents> {

    private String resourceFileName;
    private String resourceType;

    public ResourceLayerMapper(String resourceFileName, String resourceType) {
        this.resourceFileName = resourceFileName;
        this.resourceType = resourceType;
    }

    @Override
    public LayerContents apply(Resource rd) throws IOException {
        Resource r = rd.get(resourceFileName);
        Resource lr = rd.get("layer.xml");
        if (Resources.exists(r) && Resources.exists(lr)) {
            byte[] contents = r.getContents();
            byte[] lrContents = lr.getContents();
            return new LayerContents(rd, contents, lrContents);
        } else {
            DataDirectoryLoader.log(
                    Level.WARNING, "Ignoring %s directory %s", resourceType, rd.path());
            return null;
        }
    }
}

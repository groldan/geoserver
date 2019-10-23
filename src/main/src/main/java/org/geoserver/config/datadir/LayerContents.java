package org.geoserver.config.datadir;

import org.geoserver.platform.resource.Resource;

/** Layer IO resources */
final class LayerContents {
    Resource resource;
    byte[] contents;
    byte[] layerContents;

    public LayerContents(Resource resource, byte[] contents, byte[] layerContents) {
        this.resource = resource;
        this.contents = contents;
        this.layerContents = layerContents;
    }
}

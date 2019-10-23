package org.geoserver.config.datadir;

import org.geoserver.platform.resource.Resource;

/** Data store IO resources */
final class StoreContents {
    Resource resource;
    byte[] contents;

    public StoreContents(Resource resource, byte[] contents) {
        super();
        this.resource = resource;
        this.contents = contents;
    }
}

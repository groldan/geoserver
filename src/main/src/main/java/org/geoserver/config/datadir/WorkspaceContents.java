package org.geoserver.config.datadir;

import org.geoserver.platform.resource.Resource;

/** Workspace IO resources */
final class WorkspaceContents {
    Resource resource;
    byte[] contents;
    byte[] nsContents;

    public WorkspaceContents(Resource resource, byte[] contents, byte[] nsContents) {
        this.resource = resource;
        this.contents = contents;
        this.nsContents = nsContents;
    }
}

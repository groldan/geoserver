package org.geoserver.config.datadir;

import java.io.IOException;
import org.geoserver.platform.resource.Resource;
import org.geoserver.platform.resource.Resources;

/** {@link ResourceMapper} for workspaces */
final class WorkspaceMapper implements ResourceMapper<WorkspaceContents> {

    @Override
    public WorkspaceContents apply(Resource rd) throws IOException {
        Resource wr = rd.get("workspace.xml");
        Resource nr = rd.get("namespace.xml");
        if (Resources.exists(wr) && Resources.exists(nr)) {
            byte[] contents = wr.getContents();
            byte[] nrContents = nr.getContents();
            return new WorkspaceContents(rd, contents, nrContents);
        } else {
            DataDirectoryLoader.LOGGER.warning("Ignoring workspace directory " + rd.path());
            return null;
        }
    }
}

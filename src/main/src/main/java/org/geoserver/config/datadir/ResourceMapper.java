package org.geoserver.config.datadir;

import java.io.IOException;
import org.geoserver.platform.resource.Resource;

/**
 * Maps a resource into a target object
 *
 * @author Andrea Aime - GeoSolutions
 */
@FunctionalInterface
interface ResourceMapper<T> {

    T apply(Resource t) throws IOException;
}

package org.geoserver.config.datadir;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geoserver.platform.resource.Resource;
import org.geoserver.util.Filter;

class LoadResourcesTask<T> implements Callable<List<T>> {

    private Resource root;
    private Filter<Resource> filter;
    private ResourceMapper<T> mapper;

    public LoadResourcesTask(Resource root, Filter<Resource> filter, ResourceMapper<T> mapper) {
        this.root = root;
        this.filter = filter;
        this.mapper = mapper;
    }

    public @Override List<T> call() throws Exception {
        Stream<T> stream =
                root.list()
                        .stream()
                        .filter(filter::accept)
                        .map(
                                r -> {
                                    try {
                                        DataDirectoryLoader.log(
                                                Level.WARNING,
                                                "Loading resources of %s on %s",
                                                r.path(),
                                                Thread.currentThread().getName());
                                        return mapper.apply(r);
                                    } catch (Exception e) {
                                        DataDirectoryLoader.log(
                                                Level.WARNING,
                                                e,
                                                "Failed to load resource '%s'",
                                                r.name());
                                        return null;
                                    }
                                });
        return stream.filter(t -> t != null).collect(Collectors.toList());
    }
}

package org.geoserver.rest.v2.mapper;

import java.util.Optional;
import org.geoserver.ManifestLoader.AboutModel.ManifestModel;
import org.geoserver.rest.v2.api.model.ManifestEntry;
import org.geoserver.rest.v2.api.model.ModuleStatus;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jsr330")
public interface ManifestsMapper {

    default <T> T map(Optional<T> source) {
        return source.orElse(null);
    }

    ManifestEntry map(ManifestModel source);

    ModuleStatus map(org.geoserver.platform.ModuleStatus source);
}

/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.rest.v2.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geoserver.ManifestLoader;
import org.geoserver.ManifestLoader.AboutModel;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.rest.RestBaseController;
import org.geoserver.rest.v2.api.ManifestsApi;
import org.geoserver.rest.v2.api.model.ManifestEntry;
import org.geoserver.rest.v2.api.model.ModuleStatus;
import org.geoserver.rest.v2.mapper.ManifestsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** */
@RestController
@RequestMapping(path = "${geosever.rest.v2.basepath:/rest/v2}")
public class ManifestsApiController extends RestBaseController implements ManifestsApi {

    private @Autowired ManifestsMapper mapper;

    /** {@inheritDoc} */
    public @Override ResponseEntity<List<ManifestEntry>> getManifest( //
            Optional<String> regex, //
            Optional<String> from, //
            Optional<String> to, //
            Optional<String> key, //
            Optional<String> value) {

        AboutModel model = filterModel(ManifestLoader.getResources(), regex, from, to, key, value);
        return ResponseEntity.ok(
                model.getManifests().stream().map(mapper::map).collect(Collectors.toList()));
    }

    /** {@inheritDoc} */
    public @Override ResponseEntity<List<ManifestEntry>> getComponentVersions( //
            Optional<String> regex, //
            Optional<String> from, //
            Optional<String> to, //
            Optional<String> key, //
            Optional<String> value) {

        AboutModel model = filterModel(ManifestLoader.getVersions(), regex, from, to, key, value);
        return ResponseEntity.ok(
                model.getManifests().stream().map(mapper::map).collect(Collectors.toList()));
    }

    /** {@inheritDoc} */
    public @Override ResponseEntity<List<ModuleStatus>> getModulesStatus() {
        List<ModuleStatus> applicationStatus =
                getStatus().map(mapper::map).collect(Collectors.toList());
        return ResponseEntity.ok(applicationStatus);
    }

    /** {@inheritDoc} */
    public @Override ResponseEntity<ModuleStatus> getModuleStatus(String module) {
        ModuleStatus status =
                getStatus()
                        .filter(m -> module.equalsIgnoreCase(m.getModule()))
                        .findFirst() //
                        .map(mapper::map)
                        .orElseThrow(ApiException::notFound);
        return ResponseEntity.ok(status);
    }

    private Stream<org.geoserver.platform.ModuleStatus> getStatus() {
        return GeoServerExtensions.extensions(org.geoserver.platform.ModuleStatus.class).stream();
    }

    private AboutModel filterModel(
            AboutModel model, //
            Optional<String> regex, //
            Optional<String> from, //
            Optional<String> to, //
            Optional<String> key, //
            Optional<String> value) {
        if (regex.isPresent()) {
            model = model.filterNameByRegex(regex.get());
        }
        if (from.isPresent() && to.isPresent()) {
            model = model.filterNameByRange(from.get(), to.get());
        }
        if (key.isPresent()) {
            if (value.isPresent()) {
                // disregard the misleading parameter order
                model = model.filterPropertyByKeyValue(value.get(), key.get());
            } else {
                model = model.filterPropertyByKey(key.get());
            }
        } else if (value.isPresent()) {
            model = model.filterPropertyByValue(value.get());
        }
        return model;
    }
}

/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.catalogstresstool;

import static java.lang.String.format;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.wicket.Application;
import org.apache.wicket.Session;
import org.apache.wicket.ThreadContext;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.validation.validator.RangeValidator;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.WMSLayerInfoImpl;
import org.geoserver.catalog.impl.WMSStoreInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.security.SecureCatalogImpl;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.ToolPage;
import org.geotools.api.filter.Filter;
import org.geotools.util.logging.Logging;

/**
 * @see #copyOne(Catalog, CatalogInfo, String, Stopwatch, boolean, CatalogInfo)
 * @see DuplicatingCatalogVisitor
 */
@SuppressWarnings({"unchecked", "serial"})
public class CatalogStressTester extends GeoServerSecuredPage {

    static final Logger LOGGER = Logging.getLogger(CatalogStressTester.class);

    DropDownChoice<Tuple> workspace;

    DropDownChoice<Tuple> store;

    DropDownChoice<Tuple> resourceAndLayer;

    TextField<Integer> duplicateCount;

    TextField<String> sufix;

    Label progress;

    AjaxButton startLink;

    private CheckBox recursive;

    private String progressMessage = "0/0";

    private Form<?> form;

    /** DropDown choice model object becuase dbconfig freaks out if using the CatalogInfo objects directly */
    private static final class Tuple implements Serializable, Comparable<Tuple> {

        final String id, name;

        public Tuple(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public int compareTo(Tuple o) {
            return name.compareTo(o.name);
        }
    }

    private static class TupleChoiceRenderer extends ChoiceRenderer<Tuple> {

        @Override
        public Object getDisplayValue(Tuple object) {
            return object.name;
        }

        @Override
        public String getIdValue(Tuple object, int index) {
            return object.id;
        }
    }

    public CatalogStressTester() {
        super();
        setDefaultModel(new Model<>());
        add(form = new Form<>("form", new Model<>()));

        IModel<List<Tuple>> wsModel = new WorkspacesTestModel();
        workspace = new DropDownChoice<>("workspace", new Model<>(), wsModel, new TupleChoiceRenderer());
        workspace.setNullValid(true);

        workspace.setOutputMarkupId(true);
        workspace.setRequired(true);
        form.add(workspace);
        workspace.add(new OnChangeAjaxBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(store);
                target.add(resourceAndLayer);
            }
        });

        IModel<List<Tuple>> storesModel = new StoresTestModel();

        store = new DropDownChoice<>("store", new Model<>(), storesModel, new TupleChoiceRenderer());
        store.setNullValid(true);

        store.setOutputMarkupId(true);
        store.add(new OnChangeAjaxBehavior() {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.add(resourceAndLayer);
            }
        });
        form.add(store);

        IModel<List<Tuple>> resourcesModel = new ResourcesTestModel();

        resourceAndLayer =
                new DropDownChoice<>("resourceAndLayer", new Model<>(), resourcesModel, new TupleChoiceRenderer());
        resourceAndLayer.setNullValid(true);

        resourceAndLayer.setOutputMarkupId(true);
        form.add(resourceAndLayer);

        recursive = new CheckBox("recursive", new Model<>(Boolean.FALSE));
        form.add(recursive);

        duplicateCount = new TextField<>("duplicateCount", new Model<>(100), Integer.class);
        duplicateCount.setRequired(true);
        duplicateCount.add(new RangeValidator<>(1, 100000));
        form.add(duplicateCount);

        sufix = new TextField<>("sufix", new Model<>("-copy-"));
        sufix.setRequired(true);
        form.add(sufix);

        progress = new Label("progress", new IModel<>() {

            @Override
            public void detach() {}

            @Override
            public Object getObject() {
                return progressMessage;
            }

            @Override
            public void setObject(Object object) {
                progressMessage = (String) object;
            }
        });
        progress.setOutputMarkupId(true);
        progress.add(new AjaxSelfUpdatingTimerBehavior(Duration.ofSeconds(1)));
        form.add(progress);

        form.add(new AjaxButton("cancel") {

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                setResponsePage(ToolPage.class);
            }
        });

        startLink = new AjaxButton("submit", form) {

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                progress.setDefaultModelObject("");
                startLink.setVisible(false);
                target.add(startLink);
                target.add(progress);
                final Catalog catalog = getCatalog();
                Application application = getApplication();
                Session session = getSession();
                CompletableFuture.runAsync(() -> {
                    try {
                        // for #getLocalizer() to work
                        attachToCurrentThread(application, session);
                        startCopy(catalog);
                    } catch (Exception e) {
                        e.printStackTrace();
                        form.error(e.getMessage());
                        target.add(form);
                    } finally {
                        startLink.setVisible(true);
                        target.add(startLink);
                        target.add(progress);
                        dettachApplication();
                    }
                });
            }
        };
        form.add(startLink);
        startLink.setOutputMarkupId(true);
    }

    protected void onSubmit(AjaxRequestTarget target) {
        progress.setDefaultModelObject("");
        startLink.setVisible(false);
        target.add(startLink);
        target.add(progress);
        final Catalog catalog = getCatalog();
        Application application = getApplication();
        Session session = getSession();
        CompletableFuture.runAsync(() -> {
            try {
                // for #getLocalizer() to work
                attachToCurrentThread(application, session);
                startCopy(catalog);
            } catch (Exception e) {
                e.printStackTrace();
                form.error(e.getMessage());
                target.add(form);
            } finally {
                startLink.setVisible(true);
                target.add(startLink);
                target.add(progress);
                dettachApplication();
            }
        });
    }

    private Application attachToCurrentThread(Application application, Session session) {
        ThreadContext.setApplication(application);
        ThreadContext.setSession(session);
        return application;
    }

    private void dettachApplication() {
        ThreadContext.detach();
    }

    private void startCopy(Catalog catalog) {
        //        Session.get().getFeedbackMessages().clear();
        //        addFeedbackPanels(target);

        final boolean recursive = this.recursive.getModelObject();
        final int numCopies = duplicateCount.getModelObject();
        final String s = sufix.getModelObject();
        final CatalogInfo original = getOriginal(catalog);

        LOGGER.info("Creating " + numCopies + " copies of " + original + " with sufix " + s);

        Stopwatch globalTime = Stopwatch.createUnstarted();
        Stopwatch batchTime = Stopwatch.createUnstarted();
        final int padLength = (int) Math.ceil(Math.log10(numCopies));
        for (int curr = 1; curr <= numCopies; curr++) {
            String paddedIndex = Strings.padStart(String.valueOf(curr), padLength, '0');
            String nameSuffix = s + paddedIndex;
            CatalogInfo parent = null;

            batchTime.start();
            copyOne(catalog, original, nameSuffix, globalTime, recursive, parent);
            batchTime.stop();

            if (curr % 10 == 0) {
                String msg = format("inserted %s so far in %s (last 100 in %s)\n", curr, globalTime, batchTime);
                LOGGER.info(msg);
                batchTime.reset();
                progressMessage = msg;
            }
        }

        String localizerString = this.getLocalizer()
                .getString("CatalogStressTester.progressStatusMessage", this, "Inserted {0} copies of {1} in {2}");
        progressMessage = MessageFormat.format(localizerString, numCopies, original, globalTime);

        LOGGER.info(progressMessage);

        //        target.add(progress);
    }

    private CatalogInfo getOriginal(Catalog catalog) {
        CatalogInfo original;
        {
            Tuple modelObject = resourceAndLayer.getModelObject();
            if (modelObject != null) {
                original = catalog.getResource(modelObject.id, ResourceInfo.class);
            } else {
                modelObject = store.getModelObject();
                if (modelObject != null) {
                    original = catalog.getStore(modelObject.id, StoreInfo.class);
                } else {
                    modelObject = workspace.getModelObject();
                    if (modelObject != null) {
                        original = catalog.getWorkspace(modelObject.id);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
        return original;
    }

    private Class<? extends CatalogInfo> interfaceOf(CatalogInfo original) {
        Class<?>[] interfaces = {
            LayerGroupInfo.class,
            LayerInfo.class,
            NamespaceInfo.class,
            WorkspaceInfo.class,
            StyleInfo.class,
            CoverageStoreInfo.class,
            DataStoreInfo.class,
            WMSStoreInfo.class,
            CoverageInfo.class,
            FeatureTypeInfo.class,
            WMSLayerInfo.class
        };
        for (Class<?> c : interfaces) {
            if (c.isAssignableFrom(original.getClass())) {
                return (Class<? extends CatalogInfo>) c;
            }
        }
        throw new IllegalArgumentException();
    }

    private void copyOne(
            Catalog catalog,
            final CatalogInfo original,
            final String nameSuffix,
            final Stopwatch sw,
            boolean recursive,
            CatalogInfo parent) {

        final Class<? extends CatalogInfo> clazz = interfaceOf(original);
        CatalogInfo prototype = prototype(original, catalog);

        if (true) {
            UnaryOperator<String> nameMapper = origName -> origName + nameSuffix;
            BiConsumer<CatalogInfo, CatalogInfo> before = (orig, copy) -> sw.start();
            BiConsumer<CatalogInfo, CatalogInfo> after = (orig, copy) -> sw.stop();

            CatalogInfo dup = new DuplicatingCatalogVisitor(catalog)
                    .targetCatalog(catalog) // same catalog
                    .nameMapper(nameMapper)
                    .beforeListener(before)
                    .afterListener(after)
                    .recursive(recursive)
                    .duplicate(original);
            return;
        }

        try {
            OwsUtils.set(prototype, "id", null);
            OwsUtils.copy(clazz.cast(original), clazz.cast(prototype), clazz);
            final String newName = OwsUtils.get(prototype, "name") + nameSuffix;
            OwsUtils.set(prototype, "name", newName);
            if (prototype instanceof WorkspaceInfo info) {

                sw.start();
                catalog.add(info);
                sw.stop();

                String originalWsName = ((WorkspaceInfo) original).getName();
                NamespaceInfo ns = catalog.getNamespaceByPrefix(originalWsName);
                NamespaceInfoImpl ns2 = new NamespaceInfoImpl();
                ns2.setPrefix(newName);
                ns2.setURI(ns.getURI() + newName);
                sw.start();
                catalog.add(ns2);
                sw.stop();

                if (recursive) {
                    for (StoreInfo store : catalog.getStoresByWorkspace((WorkspaceInfo) original, StoreInfo.class)) {
                        copyOne(catalog, store, nameSuffix, sw, true, prototype);
                    }
                }
            } else if (prototype instanceof StoreInfo ps) {

                sw.start();
                if (parent != null) {
                    ps.setWorkspace((WorkspaceInfo) parent);
                }
                // reset the cache, or we might stumble into a error about too many connections
                // while cloning many jdbc stores
                catalog.getResourcePool().dispose();
                catalog.add(ps);
                sw.stop();

                if (recursive) {
                    for (ResourceInfo resource :
                            catalog.getResourcesByStore((StoreInfo) original, ResourceInfo.class)) {
                        copyOne(catalog, resource, nameSuffix, sw, true, prototype);
                    }
                }

            } else if (prototype instanceof ResourceInfo ri) {
                ri.setNativeName(((ResourceInfo) original).getNativeName());
                ri.setName(newName);
                if (parent != null) {
                    StoreInfo store = (StoreInfo) parent;
                    ri.setStore(store);
                    ri.setNamespace(
                            catalog.getNamespaceByPrefix(store.getWorkspace().getName()));
                }
                sw.start();
                catalog.add(ri);
                sw.stop();

                String id = prototype.getId();
                prototype = catalog.getResource(id, ResourceInfo.class);

                LayerInfo layer = resolveLayer(catalog, (ResourceInfo) original);
                if (layer == null) {
                    return;
                }
                LayerInfoImpl layerCopy;
                {
                    layerCopy = new LayerInfoImpl();
                    OwsUtils.copy(LayerInfo.class.cast(layer), layerCopy, LayerInfo.class);
                    layerCopy.setResource(ri);
                    layerCopy.setId(null);
                }
                sw.start();
                catalog.add(layerCopy);
                sw.stop();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private LayerInfo resolveLayer(Catalog catalog, final ResourceInfo original) {
        String prefixedName = ((ResourceInfo) original).prefixedName();
        return catalog.getLayerByName(prefixedName);
    }

    private CatalogInfo prototype(CatalogInfo original, Catalog catalog) {
        CatalogInfo prototype;
        if (original instanceof WorkspaceInfo) {
            prototype = new WorkspaceInfoImpl();
        } else if (original instanceof DataStoreInfo) {
            prototype = new DataStoreInfoImpl(catalog);
        } else if (original instanceof CoverageStoreInfo) {
            prototype = new CoverageStoreInfoImpl(catalog);
        } else if (original instanceof WMSStoreInfo) {
            prototype = new WMSStoreInfoImpl((CatalogImpl) SecureCatalogImpl.unwrap(catalog));
        } else if (original instanceof FeatureTypeInfo) {
            prototype = new FeatureTypeInfoImpl(catalog);
        } else if (original instanceof CoverageInfo) {
            prototype = new CoverageInfoImpl(catalog);
        } else if (original instanceof WMSLayerInfo) {
            prototype = new WMSLayerInfoImpl((CatalogImpl) SecureCatalogImpl.unwrap(catalog));
        } else {
            throw new IllegalArgumentException(original.toString());
        }
        return prototype;
    }

    private static class WorkspacesTestModel extends LoadableDetachableModel<List<Tuple>> {

        @Override
        protected List<Tuple> load() {
            Catalog catalog = GeoServerApplication.get().getCatalog();
            Filter filter = Predicates.acceptAll();

            try (CloseableIterator<WorkspaceInfo> list = catalog.list(WorkspaceInfo.class, filter, null, 4000, null)) {
                List<Tuple> workspaces = Lists.newArrayList(
                        Iterators.transform(list, input -> new Tuple(input.getId(), input.getName())));
                Collections.sort(workspaces);
                return workspaces;
            }
        }
    }

    private class ResourcesTestModel extends LoadableDetachableModel<List<Tuple>> {

        @Override
        protected List<Tuple> load() {
            Catalog catalog = getCatalog();
            Tuple storeInfo = store.getModelObject();
            if (storeInfo == null) {
                return Lists.newArrayList();
            }
            Integer limit = 100;
            Filter filter = Predicates.equal("store.id", storeInfo.id);

            try (CloseableIterator<ResourceInfo> iter = catalog.list(ResourceInfo.class, filter, null, limit, null)) {
                List<Tuple> resources = Lists.newArrayList(
                        Iterators.transform(iter, input -> new Tuple(input.getId(), input.getName())));
                Collections.sort(resources);
                return resources;
            }
        }
    }

    private class StoresTestModel extends LoadableDetachableModel<List<Tuple>> {

        @Override
        protected List<Tuple> load() {
            Catalog catalog = GeoServerApplication.get().getCatalog();
            Tuple ws = workspace.getModelObject();
            if (ws == null) {
                return Lists.newArrayList();
            }
            Filter filter = Predicates.equal("workspace.id", ws.id);
            int limit = 100;

            try (CloseableIterator<StoreInfo> iter = catalog.list(StoreInfo.class, filter, null, limit, null)) {
                List<Tuple> stores = Lists.newArrayList(
                        Iterators.transform(iter, input -> new Tuple(input.getId(), input.getName())));
                Collections.sort(stores);
                return stores;
            }
        }
    }
}

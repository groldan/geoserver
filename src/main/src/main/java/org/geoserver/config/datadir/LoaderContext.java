package org.geoserver.config.datadir;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.util.logging.Logging;

class LoaderContext {
    static Logger LOGGER = Logging.getLogger("org.geoserver");

    private final CatalogImpl catalog;
    private final ForkJoinPool PROCESSING;

    private boolean checkStores;

    private XStreamPersisterFactory persisterFactory;

    private static ThreadLocal<XStreamPersister> PERSISTER_PER_THREAD = new ThreadLocal<>();

    public LoaderContext(CatalogImpl catalog, XStreamPersisterFactory persisterFactory) {
        this.persisterFactory = persisterFactory;
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(persisterFactory);
        this.catalog = catalog;
        final int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        PROCESSING = new ForkJoinPool(parallelism);
    }

    public void dispose() {
        PROCESSING.shutdownNow();
        awaitTermination(PROCESSING);
    }

    private void awaitTermination(ForkJoinPool pool) {
        while (!pool.isTerminated()) {
            try {
                pool.awaitTermination(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public CatalogImpl catalog() {
        return catalog;
    }

    public XStreamPersister persister() {
        // Create one persister per thread. The Threadlocal will be automatically
        // discarded at #dispose as the threads in the ForkJoinPool die.
        // XStreamPersister has serious concurrency issues due to synchronized blocks in
        // XStream at com.thoughtworks.xstream.converters.reflection.FieldDictionary
        XStreamPersister xp = PERSISTER_PER_THREAD.get();
        if (xp == null) {
            xp = persisterFactory.createXMLPersister();
            xp.setCatalog(catalog);
            System.err.printf(
                    "######### Created XStreamPersister for %s%n",
                    Thread.currentThread().getName());
            PERSISTER_PER_THREAD.set(xp);
        }
        return xp;
    }

    public GeoServerResourceLoader resourceLoader() {
        return catalog().getResourceLoader();
    }

    public ForkJoinPool processingPool() {
        return PROCESSING;
    }

    public void setCheckStores(boolean checkStores) {
        this.checkStores = checkStores;
    }

    public boolean checkStores() {
        return this.checkStores;
    }
}

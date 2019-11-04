package org.geoserver.config.datadir;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.CatalogFacade;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.DefaultCatalogFacade;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.util.logging.Logging;

class LoaderContext {
    static Logger LOGGER = Logging.getLogger("org.geoserver");

    /**
     * Thread factory used to load {@link CatalogInfo}. A short lived {@link ForkJoinPool} will be
     * created with this factory and the configured {@link #DEFAULT_PARALLELISM parallelism}
     */
    private static final ForkJoinWorkerThreadFactory INITIALLIZATION_THREAD_FACTORY =
            new ForkJoinWorkerThreadFactory() {
                private AtomicInteger threadIdSeq = new AtomicInteger();

                public @Override ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                    String name =
                            String.format(
                                    "ForkJoinPool.DataDirectoryLoader-%d",
                                    threadIdSeq.incrementAndGet());
                    ForkJoinWorkerThread thread =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    thread.setName(name);
                    return thread;
                }
            };

    // empirical determination after benchmarks, the value is related not the
    // available CPUs, but by how well the disk handles parallel access, different
    // disk subsystems will have a different optimal value
    private static final int DEFAULT_PARALLELISM =
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

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
        CatalogFacade facade = DefaultCatalogFacade.unwrap(catalog.getFacade());
        if (facade instanceof DefaultCatalogFacade) {
            ((DefaultCatalogFacade) facade).lookupManager().setAutocommit(false);
        }

        int parallelism = DEFAULT_PARALLELISM;
        String value = GeoServerExtensions.getProperty("org.geoserver.catalog.loadingThreads");
        if (value != null) {
            try {
                parallelism = Integer.parseInt(value);
                LOGGER.info("Using provided catalog loader parallelism of " + parallelism);
            } catch (NumberFormatException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Parameter org.geoserver.catalog.loadingThreads is invalid, using default catalog loader parallelism of "
                                + DEFAULT_PARALLELISM);
                parallelism = DEFAULT_PARALLELISM;
            }
        }

        final UncaughtExceptionHandler uncaughtExceptionHandler =
                (thread, exception) -> {
                    LOGGER.log(
                            Level.SEVERE,
                            "Uncaught exception loading data directory at thread "
                                    + thread.getName(),
                            exception);
                };
        final boolean asyncMode = false;
        PROCESSING =
                new ForkJoinPool(
                        parallelism,
                        INITIALLIZATION_THREAD_FACTORY,
                        uncaughtExceptionHandler,
                        asyncMode);
    }

    public void commit() {
        CatalogFacade facade = DefaultCatalogFacade.unwrap(catalog.getFacade());
        if (facade instanceof DefaultCatalogFacade) {
            ((DefaultCatalogFacade) facade).lookupManager().commit();
            ((DefaultCatalogFacade) facade).lookupManager().setAutocommit(true);
        }
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

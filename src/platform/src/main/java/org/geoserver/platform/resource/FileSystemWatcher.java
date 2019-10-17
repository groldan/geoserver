/* (c) 2014-2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.platform.resource;

import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.geoserver.platform.resource.ResourceNotification.Kind;
import org.geotools.util.CanonicalSet;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Active object (using a ScheduledExecutorService) used to watch file system for changes.
 *
 * <p>This implementation currently polls the file system and should be updated with Java 7
 * WatchService when available. The internal design is similar to WatchService, WatchKey and
 * WatchEvent in order to facilitate this transition.
 *
 * <p>This implementation makes a few concessions to being associated with ResourceStore, reporting
 * changes with resource paths rather than files.
 *
 * @author Jody Garnett (Boundless)
 */
public class FileSystemWatcher implements ResourceNotificationDispatcher, DisposableBean {

    interface FileExtractor {
        public File getFile(String path);
    }

    /** Change to file system */
    static class Delta {
        final File context;

        final Kind kind;

        final List<File> created;

        final List<File> removed;

        final List<File> modified;

        public Delta(File context, Kind kind) {
            this.context = context;
            this.kind = kind;
            this.created = this.removed = this.modified = Collections.emptyList();
        }

        public Delta(
                File context,
                Kind kind,
                List<File> created,
                List<File> removed,
                List<File> modified) {
            this.context = context;
            this.kind = Kind.ENTRY_MODIFY;
            this.created = created == null ? Collections.emptyList() : created;
            this.removed = removed == null ? Collections.emptyList() : removed;
            this.modified = modified == null ? Collections.emptyList() : modified;
        }

        public int size() {
            return created.size() + removed.size() + modified.size();
        }

        @Override
        public String toString() {
            return "Delta [context="
                    + context
                    + ", created="
                    + created
                    + ", removed="
                    + removed
                    + ", modified="
                    + modified
                    + "]";
        }
    }

    /** Record of a ResourceListener that wishes to be notified of changes to a path. */
    private class Watch implements Comparable<Watch> {
        /** File being watched */
        final File file;

        /** Path to use during notification */
        final String path;

        final List<ResourceListener> listeners = new CopyOnWriteArrayList<ResourceListener>();

        /** When last notification was sent */
        long last = 0;

        /** Used to track resource creation / deletion */
        boolean exsists;

        private Map<String, Long> timetampsByPath = null;

        public Watch(File file, String path) {
            Objects.requireNonNull(file);
            Objects.requireNonNull(path);
            this.file = file;
            this.path = path;
            this.exsists = file.exists();
            this.last = exsists ? file.lastModified() : 0;
            if (file.isDirectory()) {
                debug("### Watching directory %s%n", file.getAbsolutePath());
                this.timetampsByPath = loadDirectoryContents(file);
            }
        }

        private Map<String, Long> loadDirectoryContents(File directory) {
            Map<String, Long> tsByPath;
            Stopwatch sw = Stopwatch.createStarted();
            CanonicalSet<Long> tstampDeduplicator = CanonicalSet.newInstance(Long.class);
            try (DirectoryStream<Path> dstream =
                    java.nio.file.Files.newDirectoryStream(directory.toPath())) {
                tsByPath =
                        StreamSupport.stream(dstream.spliterator(), false)
                                .map(Path::toFile) //
                                .collect(
                                        Collectors.toMap( //
                                                File::getAbsolutePath, //
                                                f ->
                                                        tstampDeduplicator.unique(
                                                                Long.valueOf(f.lastModified()))));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sw.stop();
            debug(
                    "##### loaded %,d children in %s (%,d uniq timestamps)%n",
                    tsByPath.size(), sw, tstampDeduplicator.size());
            return tsByPath;
        }

        public void addListener(ResourceListener listener) {
            listeners.add(listener);
        }

        public void removeListener(ResourceListener listener) {
            listeners.remove(listener);
        }

        /** Path used for notification */
        public String getPath() {
            return path;
        }

        public List<ResourceListener> getListeners() {
            return listeners;
        }

        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + file.hashCode();
            result = prime * result + path.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Watch)) return false;

            Watch other = (Watch) obj;
            return file.equals(other.file) && path.equals(other.path);
        }

        @Override
        public String toString() {
            return "Watch [path="
                    + path
                    + ", file="
                    + file
                    + ", listeners="
                    + listeners.size()
                    + "]";
        }

        @Override
        public int compareTo(Watch other) {
            return path.compareTo(other.path);
        }

        public Delta changed(long now) {
            if (!file.exists()) {
                Delta delta = null;
                if (this.exsists) {
                    if (this.timetampsByPath == null) {
                        // file has been deleted!
                        delta = new Delta(file, Kind.ENTRY_DELETE);
                    } else {
                        // delete directory
                        List<File> deleted =
                                timetampsByPath
                                        .keySet()
                                        .stream()
                                        .map(File::new)
                                        .collect(Collectors.toList());
                        delta = new Delta(file, Kind.ENTRY_DELETE, null, deleted, null);
                    }
                    this.last = now;
                    this.exsists = false;
                    this.timetampsByPath = null;
                }
                return delta;
            }
            if (file.isFile()) {
                long fileModified = file.lastModified();
                if (fileModified > last || !exsists) {
                    Kind kind = this.exsists ? Kind.ENTRY_MODIFY : Kind.ENTRY_CREATE;
                    this.exsists = true;
                    this.last = fileModified;
                    return new Delta(file, kind);
                } else {
                    return null; // no change!
                }
            }
            if (!file.isDirectory()) {
                return null;
            }
            Objects.requireNonNull(this.timetampsByPath);
            List<File> created = Collections.emptyList();
            List<File> modified = Collections.emptyList();

            Kind kind = null;
            long fileModified = file.lastModified();
            if (fileModified > this.last || !this.exsists) {
                kind = exsists ? Kind.ENTRY_MODIFY : Kind.ENTRY_CREATE;
                this.exsists = true;
            }
            Set<String> visited = new HashSet<>();
            try (DirectoryStream<Path> dstream =
                    java.nio.file.Files.newDirectoryStream(file.toPath())) {
                CanonicalSet<Long> tstampDeduplicator = CanonicalSet.newInstance(Long.class);
                Iterator<Path> iterator = dstream.iterator();
                while (iterator.hasNext()) {
                    final File f = iterator.next().toFile();
                    final String path = f.getAbsolutePath();
                    visited.add(path);
                    final Long tstamp = tstampDeduplicator.unique(Long.valueOf(f.lastModified()));
                    final Long previousTstamp = this.timetampsByPath.put(path, tstamp);
                    // at this point it can only be new or modified
                    if (previousTstamp == null) {
                        if (created.isEmpty()) {
                            created = new ArrayList<>();
                        }
                        created.add(f);
                    } else if (!tstamp.equals(previousTstamp)) {
                        if (modified.isEmpty()) {
                            modified = new ArrayList<>();
                        }
                        modified.add(f);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            List<File> removed = Collections.emptyList();
            if (!visited.isEmpty()) {
                Iterator<String> keys = this.timetampsByPath.keySet().iterator();
                while (keys.hasNext()) {
                    String path = keys.next();
                    if (!visited.contains(path)) {
                        if (removed.isEmpty()) {
                            removed = new ArrayList<>();
                        }
                        removed.add(new File(path));
                        keys.remove();
                    }
                }
            }
            if (kind == null) {
                if (removed.isEmpty() && created.isEmpty() && modified.isEmpty()) {
                    // win only check of directory contents
                    return null; // no change to directory contents
                } else {
                    kind = Kind.ENTRY_MODIFY;
                }
            }
            this.last = fileModified;
            return new Delta(file, kind, created, removed, modified);
        }

        public boolean isMatch(File file, String path) {
            return this.file.equals(file) && this.path.equals(path);
        }
    }

    private ScheduledExecutorService pool;

    private FileExtractor fileExtractor;

    protected long lastmodified;

    CopyOnWriteArrayList<Watch> watchers = new CopyOnWriteArrayList<Watch>();

    /**
     * Note we have a single runnable here to review all outstanding Watch instances. The focus is
     * on using minimal system resources while we wait for Java 7 WatchService (to be more
     * efficient).
     */
    private Runnable sync =
            new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    Stopwatch sw = Stopwatch.createUnstarted();
                    for (Watch watch : watchers) {
                        if (watch.getListeners().isEmpty()) {
                            watchers.remove(watch);
                            continue;
                        }
                        if (watch.file.isDirectory())
                            debug("%n### Polling changes on %s%n", watch.file);
                        sw.reset().start();
                        Delta delta = watch.changed(now);
                        sw.stop();
                        if (watch.file.isDirectory())
                            debug(
                                    "##### %,d Changes polled in %s on %s%n",
                                    delta == null ? 0 : delta.size(), sw, watch.file);

                        if (delta != null) {
                            sw.reset().start();
                            // do not call listeners on the watch thread, they may take a
                            // considerable
                            // amount of time to process the events
                            CompletableFuture.runAsync(
                                    () -> {
                                        /** Created based on created/removed/modified files */
                                        List<ResourceNotification.Event> events =
                                                ResourceNotification.delta(
                                                        watch.file,
                                                        delta.created,
                                                        delta.removed,
                                                        delta.modified);

                                        ResourceNotification notify =
                                                new ResourceNotification(
                                                        watch.getPath(),
                                                        delta.kind,
                                                        watch.last,
                                                        events);

                                        for (ResourceListener listener : watch.getListeners()) {
                                            try {
                                                listener.changed(notify);
                                            } catch (Throwable t) {
                                                Logger logger =
                                                        Logger.getLogger(
                                                                listener.getClass()
                                                                        .getPackage()
                                                                        .getName());
                                                logger.log(
                                                        Level.FINE,
                                                        "Unable to notify "
                                                                + watch
                                                                + ":"
                                                                + t.getMessage(),
                                                        t);
                                            }
                                        }
                                    });
                            debug(
                                    "##### %,d Changes dispatched in %s on %s%n",
                                    delta == null ? 0 : delta.size(), sw, watch.file);
                        }
                    }
                }
            };

    private ScheduledFuture<?> monitor;

    private TimeUnit unit = TimeUnit.SECONDS;

    private long delay = 5;

    private static CustomizableThreadFactory tFactory;

    static {
        tFactory = new CustomizableThreadFactory("FileSystemWatcher-");
        tFactory.setDaemon(true);
    }

    /**
     * FileSystemWatcher used to track file changes.
     *
     * <p>Internally a single threaded schedule executor is used to monitor files.
     */
    FileSystemWatcher(FileExtractor fileExtractor) {
        this.pool = Executors.newSingleThreadScheduledExecutor(tFactory);
        this.fileExtractor = fileExtractor;
    }

    FileSystemWatcher() {
        this(
                new FileExtractor() {
                    @Override
                    public File getFile(String path) {
                        return new File(path.replace('/', File.separatorChar));
                    }
                });
    }

    private Watch watch(File file, String path) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(path);

        for (Watch watch : watchers) {
            if (watch.isMatch(file, path)) {
                return watch;
            }
        }
        return null; // not found
    }

    public synchronized void addListener(String path, ResourceListener listener) {
        Objects.requireNonNull(path, "Path for notification is required");
        File file = fileExtractor.getFile(path);
        Objects.requireNonNull(file, "File to watch is required");
        Watch watch = watch(file, path);
        if (watch == null) {
            watch = new Watch(file, path);
            watchers.add(watch);
            if (monitor == null) {
                monitor = pool.scheduleWithFixedDelay(sync, delay, delay, unit);
            }
        }
        watch.addListener(listener);
    }

    public synchronized boolean removeListener(String path, ResourceListener listener) {
        Objects.requireNonNull(path, "Path for notification is required");
        File file = fileExtractor.getFile(path);
        Objects.requireNonNull(file, "File to watch is required");

        Watch watch = watch(file, path);
        boolean removed = false;
        if (watch != null) {
            watch.removeListener(listener);
            if (watch.getListeners().isEmpty()) {
                removed = watchers.remove(watch);
            }
        }
        if (removed && watchers.isEmpty()) {
            if (monitor != null) {
                monitor.cancel(false); // stop watching nobody is looking
                monitor = null;
            }
        }
        return removed;
    }

    /**
     * To allow test cases to set a shorter delay for testing.
     *
     * @param delay
     * @param unit
     */
    public void schedule(long delay, TimeUnit unit) {
        this.delay = delay;
        this.unit = unit;
        if (monitor != null) {
            monitor.cancel(false);
            monitor = pool.scheduleWithFixedDelay(sync, delay, delay, unit);
        }
    }

    @Override
    public void destroy() throws Exception {
        pool.shutdown();
    }

    @Override
    public void changed(ResourceNotification notification) {
        throw new UnsupportedOperationException();
    }

    private static void debug(String fmt, Object... args) {
        System.out.printf(fmt, args);
    }
}

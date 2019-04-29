/* (c) 2014-2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.platform.resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import com.google.common.base.Throwables;

/**
 * Active object (using a ScheduledExecutorService) used to watch file system
 * for changes.
 *
 * <p>
 * This implementation currently polls the file system and should be updated
 * with Java 7 WatchService when available. The internal design is similar to
 * WatchService, WatchKey and WatchEvent in order to facilitate this transition.
 *
 * <p>
 * This implementation makes a few concessions to being associated with
 * ResourceStore, reporting changes with resource paths rather than files.
 *
 * @author Jody Garnett (Boundless)
 */
public class FileSystemWatcher2 implements ResourceNotificationDispatcher, DisposableBean {

    private static final Logger LOGGER = Logging.getLogger(FileSystemWatcher2.class);

    public static @FunctionalInterface interface FileExtractor {
        public File getFile(String path);
    }

    private ConcurrentMap<Path, Watcher> watchesByDirectory = new ConcurrentHashMap<>();

    private final FileExtractor fileExtractor;

    private static CustomizableThreadFactory pollThreadFactory;
    private static CustomizableThreadFactory dispatcherThreadFactory;

    static {
        pollThreadFactory = new CustomizableThreadFactory("FileSystemWatcher-poll-");
        pollThreadFactory.setDaemon(true);

        dispatcherThreadFactory = new CustomizableThreadFactory("FileSystemWatcher-dispatcher-");
        dispatcherThreadFactory.setDaemon(true);
    }

    private ExecutorService pollService, dispatcherService;
    private WatchService watchService;

    public class PollTask implements Runnable {

        public @Override void run() {
            watchesByDirectory.values()
                    .forEach(watch -> watch.watchKey.pollEvents().forEach(event -> publish(watch, event)));
        }

        private void publish(Watcher watcher, WatchEvent<?> fsEvent) {
            // In the case of ENTRY_CREATE, ENTRY_DELETE, and ENTRY_MODIFY events the
            // context is a Path that is the relative path between the directory registered
            // with the watch service, and the entry that is created, deleted, or modified.
            Path entryRelativePath = (Path) fsEvent.context();
            Path entryConcretePath = watcher.directory.resolve(entryRelativePath);
            String path = entryRelativePath.toString();
            ResourceNotification resourceEvent;
            try {
                resourceEvent = toResourceEvent(path, entryConcretePath, fsEvent.kind());
                if (resourceEvent != null) {
                    List<ResourceListener> listeners = watcher.matchingListeners(path);
                    if (!listeners.isEmpty()) {
                        dispatcherService.submit(() -> listeners.forEach(l -> l.changed(resourceEvent)));
                    }
                }
            } catch (Exception e) {
                String msg = String.format("Error publishing %s file system event on resource %s", fsEvent.kind(),
                        entryConcretePath);
                LOGGER.log(Level.SEVERE, msg, e);
            }
        }

        private ResourceNotification toResourceEvent(String notifiedPath, Path resourcePath, WatchEvent.Kind<?> kind)
                throws IOException {
            ResourceNotification.Kind resourceEventKind;
            long timestamp = 0;
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                resourceEventKind = ResourceNotification.Kind.ENTRY_CREATE;
                timestamp = Files.readAttributes(resourcePath, BasicFileAttributes.class).creationTime().toMillis();
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                resourceEventKind = ResourceNotification.Kind.ENTRY_MODIFY;
                timestamp = Files.readAttributes(resourcePath, BasicFileAttributes.class).lastModifiedTime().toMillis();
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                resourceEventKind = ResourceNotification.Kind.ENTRY_DELETE;
            } else {
                return null;
            }
            if (0 == timestamp) {
                // it's a delete event or the file system doesn't support reading the concrete
                // file attribute
                timestamp = System.currentTimeMillis();
            }
            return new ResourceNotification(notifiedPath, resourceEventKind, timestamp);
        }
    }

    FileSystemWatcher2() {
        this(path -> new File(path.replace('/', File.separatorChar)));
    }

    /**
     * FileSystemWatcher used to track file changes.
     *
     * <p>
     * Internally a single threaded schedule executor is used to monitor files.
     */
    FileSystemWatcher2(FileExtractor fileExtractor) {
        Objects.requireNonNull(fileExtractor);
        this.fileExtractor = fileExtractor;
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.pollService = Executors.newSingleThreadExecutor(pollThreadFactory);
            this.dispatcherService = Executors.newCachedThreadPool();
        } catch (Exception e) {
            destroy();
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    public synchronized @Override void addListener(String path, ResourceListener listener) {
        Objects.requireNonNull(path, "Path for notification is required");
        Objects.requireNonNull(listener, "listener is required");

        File file = fileExtractor.getFile(path);
        Objects.requireNonNull(file, "File to watch is required");
        if (file.exists()) {
            Path watchedDirectory = file.toPath();
            String fileName = "*";
            if (!file.isDirectory()) {
                fileName = file.getName();
                watchedDirectory = watchedDirectory.getParent();
            }
            Watcher watcher = watchesByDirectory.computeIfAbsent(watchedDirectory, Watcher::new);
            watcher.addListener(fileName, listener);
        }
    }

    private class Watcher {
        private Path directory;
        private java.nio.file.WatchKey watchKey;
        private ConcurrentMap<String, ContinuationResourceListener> listenersByFileName = new ConcurrentHashMap<>();

        public Watcher(Path directory) {
            this.directory = directory;
            try {
                this.watchKey = directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public List<ResourceListener> matchingListeners(String path) {
            return listenersByFileName.entrySet().stream().filter(e -> this.matches(e.getKey(), path))
                    .map(Entry::getValue).collect(Collectors.toList());
        }

        private boolean matches(String key, String path) {
            return "*".equals(key) || key.equalsIgnoreCase(path);
        }

        public void addListener(ResourceListener listener) {
            addListener("*", listener);
        }

        public void addListener(String fileName, ResourceListener listener) {
            listenersByFileName.computeIfAbsent(fileName, fn -> new ContinuationResourceListener()).add(listener);
        }

        public void removeListener(String fileName, ResourceListener listener) {
            ContinuationResourceListener continuationResourceListener = listenersByFileName.get(fileName);
            if (null != continuationResourceListener) {
                continuationResourceListener.remove(listener);
            }
        }
    }

    private static class ContinuationResourceListener implements ResourceListener {

        private CopyOnWriteArrayList<ResourceListener> listeners = new CopyOnWriteArrayList<>();

        public List<ResourceListener> listeners() {
            return this.listeners;
        }

        public @Override void changed(ResourceNotification notify) {
            this.listeners.forEach(l -> l.changed(notify));
        }

        public void add(ResourceListener l) {
            this.listeners.add(l);
        }

        public void remove(ResourceListener l) {
            this.listeners.remove(l);
        }
    }

    private WatchKey register(Path watchedDirectory) {
        WatchKey watchKey;
        try {
            watchKey = watchedDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return watchKey;
    }

    public synchronized @Override boolean removeListener(String path, ResourceListener listener) {
        Objects.requireNonNull(path, "Path for notification is required");
        Objects.requireNonNull(listener, "listener is required");
        File file = fileExtractor.getFile(path);
        Objects.requireNonNull(file, "File to watch is required");
    }

    /**
     * To allow test cases to set a shorter delay for testing.
     *
     * @param delay
     * @param unit
     */
    public void schedule(long delay, TimeUnit unit) {
    }

    public @Override void destroy() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
            }
        }
        if (pollService != null) {
            pollService.shutdownNow();
        }
        if (dispatcherService != null) {
            dispatcherService.shutdown();
            try {
                dispatcherService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
    }

    public @Override void changed(ResourceNotification notification) {
        throw new UnsupportedOperationException();
    }

}

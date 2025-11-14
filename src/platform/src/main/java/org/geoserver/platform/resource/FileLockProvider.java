/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 *
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.logging.Logger;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.context.ServletContextAware;

/**
 * A lock provider based on file system locks
 *
 * @author Andrea Aime - GeoSolutions
 */
public class FileLockProvider implements LockProvider, ServletContextAware, DisposableBean {

    static final Logger LOGGER = Logging.getLogger(FileLockProvider.class.getName());

    private DoubleLockProvider delegate;
    private NioFileLockProvider fileLockProvider;

    private File root;

    public FileLockProvider() {
        this(null); // base directory obtained from servletContext
    }

    public FileLockProvider(File basePath) {
        this.root = basePath;
        // first off, synchronize among threads in the same jvm (the nio locks won't lock threads in
        // the same JVM)
        LockProvider memory = new MemoryLockProvider();
        // then synch up between different processes
        this.fileLockProvider = new NioFileLockProvider(getLocksFile());
        this.delegate = new DoubleLockProvider(memory, fileLockProvider);
    }

    @Override
    public Resource.Lock acquire(final String lockKey) {
        return delegate.acquire(lockKey);
    }

    @Override
    public void destroy() throws Exception {
        NioFileLockProvider flp = this.fileLockProvider;
        this.fileLockProvider = null;
        if (flp != null) {
            flp.close();
        }
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        String data = GeoServerResourceLoader.lookupGeoServerDataDirectory(servletContext);
        if (data != null) {
            root = new File(data);
        } else {
            throw new IllegalStateException("Unable to determine data directory");
        }
    }

    private File getLocksFile() {
        Objects.requireNonNull(this.root, "Root directory not set");
        File locksDir = new File(this.root, "filelocks");
        if (locksDir.isFile()) {
            throw new IllegalStateException(
                    "locks directory %s exists but it's a file".formatted(locksDir.getAbsolutePath()));
        }
        if (locksDir.mkdirs()) {
            LOGGER.fine(() -> "Created locks directory " + locksDir);
        } else if (!locksDir.isDirectory()) {
            throw new IllegalStateException(
                    "%s is not a directory or can't be created".formatted(locksDir.getAbsolutePath()));
        }

        File file = new File(locksDir, "resourcestore.locks");
        try {
            if (file.createNewFile()) {
                LOGGER.fine(() -> "Created locks file %s".formatted(file.getAbsolutePath()));
            } else if (!file.isFile()) {
                throw new IllegalStateException(
                        "%s is not a file or can't be created".formatted(locksDir.getAbsolutePath()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error creating locks file " + file.getAbsolutePath(), e);
        }

        return file;
    }

    @Override
    public String toString() {
        return "FileLockProvider " + root;
    }
}

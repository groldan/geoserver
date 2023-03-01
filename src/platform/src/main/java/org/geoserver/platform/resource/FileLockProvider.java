/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 *
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoWebCache 1.5.1 under a LGPL license
 */

package org.geoserver.platform.resource;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
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
        this.fileLockProvider = new NioFileLockProvider(this::getLocksFile);
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
        if (!locksDir.isDirectory()) {
            boolean created = locksDir.mkdirs();
            if (created && LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Created locks directory " + locksDir);
            }
            // check again in case it was created by another process/thread
            if (!locksDir.isDirectory()) {
                throw new IllegalStateException(
                        "Locks file is not a directory or can't be created: " + locksDir);
            }
        }
        return new File(locksDir, "resourcestore.locks");
    }

    @Override
    public String toString() {
        return "FileLockProvider " + root;
    }
}

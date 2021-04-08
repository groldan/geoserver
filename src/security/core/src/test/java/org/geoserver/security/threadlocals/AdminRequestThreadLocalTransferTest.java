/*
 * (c) 2017 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.security.threadlocals;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import java.util.concurrent.ExecutionException;
import org.geoserver.security.AdminRequest;
import org.geoserver.threadlocals.AbstractThreadLocalTransferTest;
import org.junit.After;
import org.junit.Test;

public class AdminRequestThreadLocalTransferTest extends AbstractThreadLocalTransferTest {

    @After
    public void cleanupThreadLocals() {
        AdminRequest.finish();
    }

    @Test
    public void testAdminRequest() throws InterruptedException, ExecutionException {
        // setup the state
        final Object myState = new Object();
        AdminRequest.start(myState);
        // test it's transferred properly using the base class machinery
        ThreadLocalTransferCallable callable =
                new ThreadLocalTransferCallable(new AdminRequestThreadLocalTransfer()) {

                    @Override
                    protected void assertThreadLocalCleaned() {
                        assertNull(AdminRequest.get());
                    }

                    @Override
                    protected void assertThreadLocalApplied() {
                        assertSame(myState, AdminRequest.get());
                    }
                };
        testThreadLocalTransfer(callable);
    }
}

/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.nsg.timeout;

import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * A simple stream that will clear the timeout verifier when the first byte is written to the output
 */
public class TimeoutCancellingStream extends ServletOutputStream {
    TimeoutVerifier timeoutVerifier;
    ServletOutputStream delegate;

    public TimeoutCancellingStream(TimeoutVerifier timeoutVerifier, ServletOutputStream delegate) {
        this.timeoutVerifier = timeoutVerifier;
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        this.timeoutVerifier.cancel();
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.timeoutVerifier.cancel();
        super.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.timeoutVerifier.cancel();
        super.write(b, off, len);
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        delegate.setWriteListener(writeListener);
    }
}

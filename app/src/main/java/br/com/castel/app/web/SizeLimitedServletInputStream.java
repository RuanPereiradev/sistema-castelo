package br.com.castel.app.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;

/**
 * Wraps the body stream and fails as soon as one byte past the limit is read.
 *
 * <p>Counts, never buffers: the bytes already read are handed to the caller and forgotten, so a
 * body of any size costs constant memory. This is what protects a request that declares no
 * {@code Content-Length} (chunked transfer), where the header check has nothing to look at.
 */
final class SizeLimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maximumBytes;

    private long bytesRead;

    SizeLimitedServletInputStream(ServletInputStream delegate, long maximumBytes) {
        this.delegate = delegate;
        this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
        int byteRead = delegate.read();
        if (byteRead != -1) {
            count(1);
        }
        return byteRead;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = delegate.read(buffer, offset, length);
        if (read > 0) {
            count(read);
        }
        return read;
    }

    private void count(int justRead) throws RequestBodyTooLargeException {
        bytesRead += justRead;
        if (bytesRead > maximumBytes) {
            throw new RequestBodyTooLargeException(maximumBytes);
        }
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        delegate.setReadListener(readListener);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}

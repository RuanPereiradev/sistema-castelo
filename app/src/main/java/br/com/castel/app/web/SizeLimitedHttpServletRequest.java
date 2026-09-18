package br.com.castel.app.web;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body can only be read up to a maximum number of bytes.
 *
 * <p>Both {@link #getInputStream()} and {@link #getReader()} are overridden: the wrapper's default
 * {@code getReader()} goes to the wrapped request's own reader and would bypass the counter.
 */
final class SizeLimitedHttpServletRequest extends HttpServletRequestWrapper {

    private final long maximumBytes;

    private ServletInputStream inputStream;
    private BufferedReader reader;

    SizeLimitedHttpServletRequest(HttpServletRequest request, long maximumBytes) {
        super(request);
        this.maximumBytes = maximumBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = new SizeLimitedServletInputStream(super.getInputStream(), maximumBytes);
        }
        return inputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(getInputStream(), requestCharset()));
        }
        return reader;
    }

    private Charset requestCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (RuntimeException unsupported) {
            return StandardCharsets.UTF_8;
        }
    }
}

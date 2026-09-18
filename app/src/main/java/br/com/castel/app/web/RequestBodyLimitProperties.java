package br.com.castel.app.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Maximum size of a request body accepted anywhere in the API.
 *
 * <p>The largest legitimate body foreseen for v1 is a tab with many items, in the order of a few
 * kilobytes, so the 64 KB default leaves an order of magnitude of headroom while closing the 2 MB
 * the container accepted before (decision #9).
 *
 * @param maxRequestBodySize the limit; a body above it answers 413 {@code REQUEST_BODY_TOO_LARGE}
 */
@ConfigurationProperties(prefix = "castel.web")
public record RequestBodyLimitProperties(DataSize maxRequestBodySize) {

    public RequestBodyLimitProperties {
        if (maxRequestBodySize == null || maxRequestBodySize.toBytes() <= 0) {
            throw new IllegalStateException(
                    "castel.web.max-request-body-size must be a positive size, for instance 64KB");
        }
    }

    public long maxRequestBodyBytes() {
        return maxRequestBodySize.toBytes();
    }
}

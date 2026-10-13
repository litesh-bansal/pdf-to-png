package org.example.pdf;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code pdf.render.*} prefix in {@code application.properties}. Spring Boot
 * binds them onto this record (kebab-case keys map to camelCase components, e.g.
 * {@code pdf.render.acquire-timeout} → {@code acquireTimeout}); the compact constructor fills in
 * defaults for anything left unset.
 *
 * @param concurrency    number of independent renderer instances. Each one is a separate copy of
 *                       the native libraries made at startup, and is one unit of render
 *                       parallelism. Defaults to 4.
 * @param acquireTimeout how long a request waits for a free renderer before failing with 503
 * @param defaultDpi     resolution used when a caller does not specify one
 * @param maxDpi         upper bound accepted from callers, guarding against huge allocations
 */
@ConfigurationProperties(prefix = "pdf.render")
public record PdfRenderProperties(
        int concurrency,
        Duration acquireTimeout,
        int defaultDpi,
        int maxDpi) {

    public PdfRenderProperties {
        if (concurrency <= 0) {
            concurrency = 4;
        }
        if (acquireTimeout == null) {
            acquireTimeout = Duration.ofSeconds(30);
        }
        if (defaultDpi <= 0) {
            defaultDpi = 150;
        }
        if (maxDpi <= 0) {
            maxDpi = 600;
        }
    }
}

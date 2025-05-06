package cz.cvut.kbss.termit.service.validation;

import java.net.URI;

/**
 * Severity of SHACL rule violation.
 */
enum ShaclSeverity {

    VIOLATION("http://www.w3.org/ns/shacl#Violation"),
    WARNING("http://www.w3.org/ns/shacl#Warning"),
    INFO("http://www.w3.org/ns/shacl#Info");

    private final URI uri;

    ShaclSeverity(final String uri) {
        this.uri = URI.create(uri);
    }

    public URI getUri() {
        return uri;
    }
}

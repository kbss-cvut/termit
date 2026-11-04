package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

import java.net.URI;
import java.util.Optional;

/**
 * Indicates that outgoing references from the specified term need to be updated due to a change in corresponding
 * inverse relationships.
 *
 * <p>For example, when a term becomes referenced as {@literal skos:broader}, its subterms need to be updated
 * accordingly.
 */
public class TermReferencesUpdatedEvent extends ApplicationEvent {

    private final URI termUri;

    private final String property;

    private URI context;

    public TermReferencesUpdatedEvent(Object source, URI termUri, String property) {
        super(source);
        this.termUri = termUri;
        this.property = property;
    }

    public TermReferencesUpdatedEvent(Object source, URI termUri, String property, URI context) {
        this(source, termUri, property);
        this.context = context;
    }

    public URI getTermUri() {
        return termUri;
    }

    public String getProperty() {
        return property;
    }

    public Optional<URI> getContext() {
        return Optional.ofNullable(context);
    }
}

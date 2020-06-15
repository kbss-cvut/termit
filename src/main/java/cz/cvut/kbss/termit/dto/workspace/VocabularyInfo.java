package cz.cvut.kbss.termit.dto.workspace;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * Data about vocabulary loaded in a workspace.
 * <p>
 * Most importantly, it points to the relevant repository contexts.
 */
@SparqlResultSetMapping(name = "VocabularyInfo", classes = {@ConstructorResult(targetClass = VocabularyInfo.class,
        variables = {
                @VariableResult(name = "entity", type = URI.class),
                @VariableResult(name = "context", type = URI.class),
                @VariableResult(name = "changeTrackingContext", type = URI.class)
        })})
public class VocabularyInfo implements Serializable {

    private URI uri;

    private URI context;

    private URI changeTrackingContext;

    public VocabularyInfo(URI uri, URI context, URI changeTrackingContext) {
        this.uri = uri;
        this.context = context;
        this.changeTrackingContext = changeTrackingContext;
    }

    /**
     * Identifier of the vocabulary.
     */
    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    /**
     * Identifier of the context in which this vocabulary is stored.
     */
    public URI getContext() {
        return context;
    }

    public void setContext(URI context) {
        this.context = context;
    }

    /**
     * Identifier of the context containing change tracking records.
     */
    public URI getChangeTrackingContext() {
        return changeTrackingContext;
    }

    public void setChangeTrackingContext(URI changeTrackingContext) {
        this.changeTrackingContext = changeTrackingContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VocabularyInfo)) {
            return false;
        }
        VocabularyInfo that = (VocabularyInfo) o;
        return Objects.equals(uri, that.uri) &&
                Objects.equals(context, that.context) &&
                Objects.equals(changeTrackingContext, that.changeTrackingContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, context, changeTrackingContext);
    }

    @Override
    public String toString() {
        return "VocabularyInfo{" +
                "<" + uri +
                ">, context=<" + context +
                ">, changeTrackingContext=<" + changeTrackingContext +
                ">}";
    }
}

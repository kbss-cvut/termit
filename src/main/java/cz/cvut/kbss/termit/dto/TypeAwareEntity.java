package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.util.Constants;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SparqlResultSetMapping(
        name = TypeAwareEntity.RESULTSET_MAPPING,
        classes = {
            @ConstructorResult(
                    targetClass = TypeAwareEntity.class,
                    variables = {
                        @VariableResult(name = "entity", type = URI.class),
                        @VariableResult(name = "types", type = String.class)
                    })
        })
public class TypeAwareEntity {
    public static final String RESULTSET_MAPPING = "TypeAwareEntity";

    private final URI uri;

    private final Set<String> types;

    public TypeAwareEntity(URI uri, Set<String> types) {
        this.uri = uri;
        this.types = types;
    }

    /**
     * @param types Type identifiers aggregated using {@literal GROUP_CONCAT} with
     *     {@link Constants#GROUP_CONCAT_SEPARATOR}
     */
    public TypeAwareEntity(URI uri, String types) {
        this(uri, new HashSet<>(Arrays.asList(types.split(Constants.GROUP_CONCAT_SEPARATOR))));
    }

    public boolean isTerm() {
        return types.contains(SKOS.CONCEPT);
    }

    public boolean isCollection() {
        return types.contains(SKOS.COLLECTION) || types.contains(SKOS.ORDERED_COLLECTION);
    }

    public URI getUri() {
        return uri;
    }

    public Set<String> getTypes() {
        return types;
    }
}

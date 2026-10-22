package cz.cvut.kbss.termit.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/** Description of {@link SKOS#COLLECTION SKOS Collection} without its members */
@OWLClass(iri = SKOS.COLLECTION)
@JsonPropertyOrder({"uri", "label", "description", "vocabulary"})
public class SkosCollectionDto implements HasIdentifier, HasTypes {
    @Id
    private URI uri;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private MultilingualString description;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = SKOS.IN_SCHEME)
    private URI vocabulary;

    @Types(readOnly = true)
    private Set<String> types;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SkosCollectionDto that)) return false;
        return Objects.equals(uri, that.uri)
                && Objects.equals(label, that.label)
                && Objects.equals(description, that.description)
                && Objects.equals(vocabulary, that.vocabulary)
                && Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label, description, vocabulary, types);
    }

    public boolean isOrdered() {
        return hasType(SKOS.ORDERED_COLLECTION);
    }
}

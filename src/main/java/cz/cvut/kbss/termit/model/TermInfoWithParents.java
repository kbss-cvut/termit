package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.dto.TermDescription;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "vocabulary", "state", "parentTerms"})
public class TermInfoWithParents implements TermDescription {

    @Id
    private URI uri;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @Inferred
    @OWLObjectProperty(iri = SKOS.IN_SCHEME)
    private URI vocabulary;

    @OWLObjectProperty(iri = Vocabulary.s_p_has_state_of_term)
    private URI state;

    /**
     * Parent terms from the same vocabulary.
     */
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER, cascade = {CascadeType.DETACH})
    private Set<TermInfoWithParents> parentTerms;

    @Types
    private Set<String> types;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    @Override
    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public URI getState() {
        return state;
    }

    public void setState(URI state) {
        this.state = state;
    }

    public Set<TermInfoWithParents> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<TermInfoWithParents> parentTerms) {
        this.parentTerms = parentTerms;
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof TermInfoWithParents termInfoWithParents)) {
            return false;
        }
        return Objects.equals(uri, termInfoWithParents.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public String toString() {
        return "TermInfoWithParents{" + label + " " + Utils.uriToString(uri) + '}';
    }
}

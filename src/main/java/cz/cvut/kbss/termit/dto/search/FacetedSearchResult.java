package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the result of a faceted term search.
 * <p>
 * Contains only basic SKOS properties.
 */
@OWLClass(iri = SKOS.CONCEPT)
public class FacetedSearchResult extends Asset<MultilingualString> implements HasTypes {

    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = SKOS.DEFINITION)
    private MultilingualString definition;

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.HIDDEN_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLDataProperty(iri = SKOS.NOTATION, simpleLiteral = true)
    private Set<String> notations;

    @OWLAnnotationProperty(iri = SKOS.EXAMPLE)
    private Set<MultilingualString> examples;

    @Inferred
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @OWLDataProperty(iri = Vocabulary.s_p_je_draft)
    private Boolean draft;

    @Types
    private Set<String> types;

    @Override
    public MultilingualString getLabel() {
        return label;
    }

    @Override
    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getDefinition() {
        return definition;
    }

    public void setDefinition(MultilingualString definition) {
        this.definition = definition;
    }

    public Set<MultilingualString> getAltLabels() {
        return altLabels;
    }

    public void setAltLabels(Set<MultilingualString> altLabels) {
        this.altLabels = altLabels;
    }

    public Set<MultilingualString> getHiddenLabels() {
        return hiddenLabels;
    }

    public void setHiddenLabels(Set<MultilingualString> hiddenLabels) {
        this.hiddenLabels = hiddenLabels;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public Set<String> getNotations() {
        return notations;
    }

    public void setNotations(Set<String> notations) {
        this.notations = notations;
    }

    public Set<MultilingualString> getExamples() {
        return examples;
    }

    public void setExamples(Set<MultilingualString> examples) {
        this.examples = examples;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    public Boolean isDraft() {
        return draft == null || draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FacetedSearchResult)) {
            return false;
        }
        FacetedSearchResult that = (FacetedSearchResult) o;
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }

    @Override
    public String toString() {
        return "FacetedSearchResult{" +
                getLabel() + ' ' +
                Utils.uriToString(getUri()) +
                ", types=" + getTypes() +
                '}';
    }
}
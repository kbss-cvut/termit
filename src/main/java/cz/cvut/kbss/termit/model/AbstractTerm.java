package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.util.Vocabulary;
import cz.cvut.kbss.termit.validation.PrimaryNotBlank;

import java.io.Serializable;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@MappedSuperclass
public abstract class AbstractTerm extends Asset<MultilingualString> implements Serializable {

    @PrimaryNotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = SKOS.DEFINITION)
    private MultilingualString definition;

    @Transient  // Not used by JOPA
    @OWLObjectProperty(iri = SKOS.NARROWER) // But map the property for JSON-LD serialization
    private Set<TermInfo> subTerms;

    @OWLObjectProperty(iri = SKOS.IN_SCHEME)
    private URI glossary;

    @Inferred
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @OWLDataProperty(iri = Vocabulary.s_p_je_draft)
    private Boolean draft;

    public AbstractTerm() {
    }

    protected AbstractTerm(AbstractTerm other) {
        Objects.requireNonNull(other);
        setUri(other.getUri());
        if (other.getLabel() != null) {
            this.label = new MultilingualString(other.getLabel().getValue());
        }
        if (other.getDefinition() != null) {
            this.definition = new MultilingualString(other.getDefinition().getValue());
        }
        this.draft = other.draft;
        this.glossary = other.glossary;
        this.vocabulary = other.vocabulary;
        if (other.getSubTerms() != null) {
            this.subTerms = other.getSubTerms().stream().map(TermInfo::new).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

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

    public Set<TermInfo> getSubTerms() {
        return subTerms;
    }

    public void setSubTerms(Set<TermInfo> subTerms) {
        this.subTerms = subTerms;
    }

    public URI getGlossary() {
        return glossary;
    }

    public void setGlossary(URI glossary) {
        this.glossary = glossary;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    public Boolean isDraft() {
        return draft == null ? true : draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTerm)) return false;
        AbstractTerm that = (AbstractTerm) o;
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }
}

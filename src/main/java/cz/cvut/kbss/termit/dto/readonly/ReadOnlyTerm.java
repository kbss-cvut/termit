package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OWLClass(iri = SKOS.CONCEPT)
public class ReadOnlyTerm implements HasIdentifier, HasTypes, Serializable {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private Set<String> hiddenLabels;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @OWLAnnotationProperty(iri = SKOS.DEFINITION)
    private MultilingualString definition;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.BROADER)
    private Set<ReadOnlyTerm> parentTerms;

    @OWLObjectProperty(iri = SKOS.NARROWER)
    private Set<TermInfo> subTerms;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @Types
    private Set<String> types;

    public ReadOnlyTerm() {
    }

    public ReadOnlyTerm(Term term) {
        Objects.requireNonNull(term);
        this.uri = term.getUri();
        this.label = term.getLabel();
        if (term.getAltLabels() != null) {
            this.altLabels = new HashSet<>(term.getAltLabels());
        }
        if (term.getHiddenLabels() != null) {
            this.hiddenLabels = new HashSet<>(term.getHiddenLabels());
        }
        if (term.getDefinition() != null) {
            this.definition = new MultilingualString(term.getDefinition().getValue());
        }
        this.description = term.getDescription();
        this.vocabulary = term.getVocabulary();
        if (term.getSources() != null) {
            this.sources = new HashSet<>(term.getSources());
        }
        if (term.getParentTerms() != null) {
            this.parentTerms = term.getParentTerms().stream().map(ReadOnlyTerm::new).collect(Collectors.toSet());
        }
        if (term.getSubTerms() != null) {
            this.subTerms = new HashSet<>(term.getSubTerms());
        }
        if (term.getTypes() != null) {
            this.types = new HashSet<>(term.getTypes());
        }
    }

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

    public Set<MultilingualString> getAltLabels() {
        return altLabels;
    }

    public void setAltLabels(Set<MultilingualString> altLabels) {
        this.altLabels = altLabels;
    }

    public Set<String> getHiddenLabels() {
        return hiddenLabels;
    }

    public void setHiddenLabels(Set<String> hiddenLabels) {
        this.hiddenLabels = hiddenLabels;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MultilingualString getDefinition() {
        return definition;
    }

    public void setDefinition(MultilingualString definition) {
        this.definition = definition;
    }

    public Set<String> getSources() {
        return sources;
    }

    public void setSources(Set<String> sources) {
        this.sources = sources;
    }

    public Set<ReadOnlyTerm> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<ReadOnlyTerm> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<TermInfo> getSubTerms() {
        return subTerms;
    }

    public void setSubTerms(Set<TermInfo> subTerms) {
        this.subTerms = subTerms;
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadOnlyTerm)) {
            return false;
        }
        ReadOnlyTerm that = (ReadOnlyTerm) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public String toString() {
        return "ReadOnlyTerm{" +
                label +
                " <" + uri + '>' +
                ", types=" + types +
                '}';
    }
}

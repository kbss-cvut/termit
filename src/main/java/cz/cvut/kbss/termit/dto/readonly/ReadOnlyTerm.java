package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.util.HasTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@OWLClass(iri = SKOS.CONCEPT)
public class ReadOnlyTerm extends AbstractTerm implements HasTypes {

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.BROADER)
    private Set<ReadOnlyTerm> parentTerms;

    @OWLObjectProperty(iri = SKOS.NOTATION)
    private String notation;

    @Types
    private Set<String> types;

    public ReadOnlyTerm() {
    }

    public ReadOnlyTerm(Term term) {
        super(term);
        if (term.getAltLabels() != null) {
            this.altLabels = new HashSet<>(term.getAltLabels());
        }
        if (term.getHiddenLabels() != null) {
            this.hiddenLabels = new HashSet<>(term.getHiddenLabels());
        }
        if (term.getDescription() != null) {
            this.description = new MultilingualString(term.getDescription().getValue());
        }
        if (term.getSources() != null) {
            this.sources = new HashSet<>(term.getSources());
        }
        if (term.getParentTerms() != null) {
            this.parentTerms = term.getParentTerms().stream().map(ReadOnlyTerm::new).collect(Collectors.toSet());
        }
        if (term.getProperties() != null && term.getProperties().containsKey(SKOS.NOTATION)) {
            final Set<String> set = term.getProperties().get(SKOS.NOTATION);
            if ( set.size() == 1 ) {
                this.notation = set.iterator().next();
            }
        }
        if (term.getTypes() != null) {
            this.types = new HashSet<>(term.getTypes());
        }
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

    public String getNotation() {
        return notation;
    }

    public void setNotation(String notation) {
        this.notation = notation;
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
    public String toString() {
        return "ReadOnlyTerm{" +
                getLabel() +
                " <" + getUri() + '>' +
                ", types=" + types +
                '}';
    }
}

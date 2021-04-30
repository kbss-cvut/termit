package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.*;
import java.util.stream.Collectors;

@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "description", "subTerms"})
public class TermDto extends AbstractTerm implements HasTypes {

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.HIDDEN_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.BROADER)
    private Set<Term> parentTerms;

    @OWLObjectProperty(iri = SKOS.RELATED)
    private Set<TermInfo> related = new LinkedHashSet<>();

    // relatedMatch are related terms from a different vocabulary
    @OWLObjectProperty(iri = SKOS.RELATED_MATCH)
    private Set<TermInfo> relatedMatch = new LinkedHashSet<>();

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj_definice_termu)
    private TermDefinitionSource definitionSource;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    @Types
    private Set<String> types;

    public TermDto() {
    }

    public TermDto(Term term) {
        super(term);
        if (term.getAltLabels() != null) {
            this.altLabels = term.getAltLabels().stream().map(ms -> new MultilingualString(ms.getValue())).collect(Collectors.toSet());
        }
        if (term.getHiddenLabels() != null) {
            this.hiddenLabels = term.getHiddenLabels().stream().map(ms -> new MultilingualString(ms.getValue())).collect(Collectors.toSet());
        }
        if (term.getDescription() != null) {
            this.description = new MultilingualString(term.getDescription().getValue());
        }
        if (term.getSources() != null) {
            this.sources = new HashSet<>(term.getSources());
        }
        if (term.getDefinitionSource() != null) {
            // Just a shallow copy, hopefully will be enough
            this.definitionSource = new TermDefinitionSource(term.getDefinitionSource().getTerm(), term.getDefinitionSource().getTarget());
        }
        if (term.getRelated() != null) {
            this.related = term.getRelated().stream().map(TermInfo::new).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (term.getInverseRelated() != null) {
            term.getInverseRelated().forEach(ti -> related.add(new TermInfo(ti)));
        }

        if (term.getRelatedMatch() != null) {
            this.relatedMatch = term.getRelatedMatch().stream().map(TermInfo::new).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (term.getInverseRelatedMatch() != null) {
            term.getInverseRelatedMatch().forEach(ti -> relatedMatch.add(new TermInfo(ti)));
        }
        if (term.getProperties() != null) {
            this.properties = new HashMap<>();
            term.getProperties().forEach((k, v) -> properties.put(k, new HashSet<>(v)));
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

    public Set<Term> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<Term> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<TermInfo> getRelated() {
        return related;
    }

    public void setRelated(Set<TermInfo> related) {
        this.related = related;
    }

    public Set<TermInfo> getRelatedMatch() {
        return relatedMatch;
    }

    public void setRelatedMatch(Set<TermInfo> relatedMatch) {
        this.relatedMatch = relatedMatch;
    }

    public TermDefinitionSource getDefinitionSource() {
        return definitionSource;
    }

    public void setDefinitionSource(TermDefinitionSource definitionSource) {
        this.definitionSource = definitionSource;
    }

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<String>> properties) {
        this.properties = properties;
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
        return "Term{" +
                getLabel() +
                " <" + getUri() + '>' +
                ", types=" + types +
                '}';
    }
}

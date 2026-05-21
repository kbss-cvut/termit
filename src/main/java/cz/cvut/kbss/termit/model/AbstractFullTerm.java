package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.MappedSuperclass;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.Transient;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@MappedSuperclass
public abstract class AbstractFullTerm extends AbstractTerm implements HasTypes {

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

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.EXACT_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> exactMatchTerms;

    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseExactMatchTerms;

    @OWLObjectProperty(iri = SKOS.RELATED, fetch = FetchType.EAGER)
    private Set<TermInfo> related;

    // Terms related by the virtue of related being symmetric, i.e. those that assert relation with this term
    // Loaded outside of JOPA entity loading mechanism
    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseRelated;

    // relatedMatch are related terms from a different vocabulary
    @OWLObjectProperty(iri = SKOS.RELATED_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> relatedMatch;

    // Terms from a different vocabulary related by the virtue of relatedMatch being symmetric, i.e. those that assert relation with this term
    // Loaded outside of JOPA entity loading mechanism
    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseRelatedMatch;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_has_term_definition_source, fetch = FetchType.EAGER)
    private TermDefinitionSource definitionSource;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<Object>> properties;

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

    public Set<String> getSources() {
        return sources;
    }

    public void setSources(Set<String> sources) {
        this.sources = sources;
    }

    public Set<TermInfo> getExactMatchTerms() {
        return exactMatchTerms;
    }

    public void setExactMatchTerms(Set<TermInfo> exactMatchTerms) {
        this.exactMatchTerms = exactMatchTerms;
    }

    public void addExactMatch(TermInfo term) {
        Objects.requireNonNull(term);
        if (exactMatchTerms == null) {
            setExactMatchTerms(new HashSet<>());
        }
        exactMatchTerms.add(term);
    }

    public Set<TermInfo> getInverseExactMatchTerms() {
        return inverseExactMatchTerms;
    }

    public void setInverseExactMatchTerms(Set<TermInfo> inverseExactMatchTerms) {
        this.inverseExactMatchTerms = inverseExactMatchTerms;
    }

    public Set<TermInfo> getRelated() {
        return related;
    }

    public void setRelated(Set<TermInfo> related) {
        this.related = related;
    }

    public void addRelatedTerm(TermInfo ti) {
        Objects.requireNonNull(ti);
        if (related == null) {
            setRelated(new LinkedHashSet<>());
        }
        related.add(ti);
    }

    public Set<TermInfo> getInverseRelated() {
        return inverseRelated;
    }

    public void setInverseRelated(Set<TermInfo> inverseRelated) {
        this.inverseRelated = inverseRelated;
    }

    public Set<TermInfo> getRelatedMatch() {
        return relatedMatch;
    }

    public void setRelatedMatch(Set<TermInfo> relatedMatch) {
        this.relatedMatch = relatedMatch;
    }

    public void addRelatedMatchTerm(TermInfo ti) {
        Objects.requireNonNull(ti);
        if (relatedMatch == null) {
            setRelatedMatch(new LinkedHashSet<>());
        }
        relatedMatch.add(ti);
    }

    public Set<TermInfo> getInverseRelatedMatch() {
        return inverseRelatedMatch;
    }

    public void setInverseRelatedMatch(Set<TermInfo> inverseRelatedMatch) {
        this.inverseRelatedMatch = inverseRelatedMatch;
    }

    public TermDefinitionSource getDefinitionSource() {
        return definitionSource;
    }

    public void setDefinitionSource(TermDefinitionSource definitionSource) {
        this.definitionSource = definitionSource;
    }

    public Map<String, Set<Object>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<Object>> properties) {
        this.properties = properties;
    }

    /**
     * Consolidates the asserted related (relatedMatch, exactMatch) and inferred inverse related (relatedMatch,
     * exactMatch) terms into related (relatedMatch, exactMatch).
     * <p>
     * This basically means copying items from {@code inverseRelated} ({@code inverseRelatedMatch}, {@code exactMatch})
     * to {@code related} ({@code relatedMatch}, {@code exactMatch}) so that they act as they should in reality because
     * of skos:related (skos:relatedMatch, skos:exactMatch) being symmetric.
     */
    public void consolidateInferred() {
        if (inverseRelated != null) {
            inverseRelated.forEach(ti -> addRelatedTerm(new TermInfo(ti)));
        }
        if (inverseRelatedMatch != null) {
            inverseRelatedMatch.forEach(ti -> addRelatedMatchTerm(new TermInfo(ti)));
        }
        if (inverseExactMatchTerms != null) {
            inverseExactMatchTerms.forEach(ti -> addExactMatch(new TermInfo(ti)));
        }
    }
}

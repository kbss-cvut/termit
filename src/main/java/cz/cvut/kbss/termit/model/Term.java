/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.Transient;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Audited
@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "description", "subTerms"})
@JsonIgnoreProperties({"persistenceContext"})
public class Term extends AbstractTerm implements HasTypes, SupportsSnapshots {

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

    /**
     * Parent terms from the same vocabulary.
     */
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<Term> parentTerms;

    /**
     * Parent terms from different vocabularies.
     * <p>
     * Represents the {@code skos:broadMatch} property.
     */
    @JsonIgnore
    @OWLObjectProperty(iri = SKOS.BROAD_MATCH, fetch = FetchType.EAGER)
    private Set<Term> externalParentTerms;

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
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj_definice_termu, fetch = FetchType.EAGER)
    private TermDefinitionSource definitionSource;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    public Term() {
    }

    public Term(URI uri) {
        setUri(uri);
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

    public Set<Term> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<Term> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<Term> getExternalParentTerms() {
        return externalParentTerms;
    }

    public void setExternalParentTerms(Set<Term> externalParentTerms) {
        this.externalParentTerms = externalParentTerms;
    }

    /**
     * Adds the specified term to the parent terms of this instance.
     * <p>
     * If the specified term is from the same glossary, it is added to {@code parentTerms}, otherwise, it is added to
     * the {@code importedParentTerms}.
     *
     * @param term Term to add as parent
     */
    public void addParentTerm(Term term) {
        Objects.requireNonNull(term);
        if (!Objects.equals(getGlossary(), term.getGlossary())) {
            if (externalParentTerms == null) {
                setExternalParentTerms(new HashSet<>());
            }
            externalParentTerms.add(term);
        } else {
            if (parentTerms == null) {
                setParentTerms(new HashSet<>());
            }
            parentTerms.add(term);
        }
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

    public void setExactMatchTerms(Set<TermInfo> exactMatchTerms) {
        this.exactMatchTerms = exactMatchTerms;
    }

    public Set<TermInfo> getExactMatchTerms() {
        return exactMatchTerms;
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

    public Set<String> getSources() {
        return sources;
    }

    public void setSources(Set<String> source) {
        this.sources = source;
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

    /**
     * Checks whether this term has a parent term in the same vocabulary.
     *
     * @return Whether this term has a parent in its vocabulary. Returns {@code false} also if this term has no parent
     * term at all.
     */
    public boolean hasParentInSameVocabulary() {
        return parentTerms != null && parentTerms.stream().anyMatch(p -> p.getGlossary().equals(getGlossary()));
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

    /**
     * Consolidates parent and external parent terms into just parent terms.
     * <p>
     * This is based on the fact that external parents are a special case of parent terms (SKOS broadMatch is a
     * sub-property of broader). Clients need not know about their distinction, which is important only at repository
     * level.
     *
     * @see #splitExternalAndInternalParents()
     */
    public void consolidateParents() {
        if (externalParentTerms != null && !externalParentTerms.isEmpty()) {
            if (parentTerms == null) {
                setParentTerms(new LinkedHashSet<>());
            }
            parentTerms.addAll(externalParentTerms);
        }
    }

    /**
     * Splits consolidated parent terms into external and (internal) parent terms.
     * <p>
     * This split is driven by the fact that external parents belong to a different glossary than this term and should
     * thus be differentiated on repository level.
     * <p>
     * This method does the inverse of {@link #consolidateParents()}.
     *
     * @see #consolidateParents()
     */
    public void splitExternalAndInternalParents() {
        if (parentTerms == null || parentTerms.isEmpty()) {
            return;
        }
        final Set<Term> parents = new LinkedHashSet<>();
        final Set<Term> externalParents = new LinkedHashSet<>();
        for (Term p : parentTerms) {
            if (Objects.equals(getGlossary(), p.getGlossary())) {
                parents.add(p);
            } else {
                externalParents.add(p);
            }
        }
        this.parentTerms = parents;
        this.externalParentTerms = externalParents;
    }
}

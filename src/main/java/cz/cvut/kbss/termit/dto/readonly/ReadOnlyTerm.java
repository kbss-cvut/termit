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
package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OWLClass(iri = SKOS.CONCEPT)
public class ReadOnlyTerm extends AbstractTerm {

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.HIDDEN_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.BROADER)
    private Set<ReadOnlyTerm> parentTerms;

    @OWLDataProperty(iri = SKOS.NOTATION, simpleLiteral = true)
    private Set<String> notations;

    @OWLAnnotationProperty(iri = SKOS.EXAMPLE)
    private Set<MultilingualString> examples;

    @OWLObjectProperty(iri = SKOS.EXACT_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> exactMatchTerms;

    @OWLObjectProperty(iri = SKOS.RELATED, fetch = FetchType.EAGER)
    private Set<TermInfo> related;

    @OWLObjectProperty(iri = SKOS.RELATED_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> relatedMatch;

    @Properties
    private Map<String, Set<String>> properties;

    public ReadOnlyTerm() {
        // Public no-arg constructor for marshalling support
    }

    public ReadOnlyTerm(final Term term) {
        this(term, Collections.emptySet());
    }

    public ReadOnlyTerm(final Term term, final Collection<String> propertiesToExport) {
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
            this.parentTerms = term.getParentTerms().stream().map(pTerm -> new ReadOnlyTerm(pTerm, propertiesToExport))
                                   .collect(Collectors.toSet());
        }
        if (term.getRelated() != null) {
            this.related = new HashSet<>(term.getRelated());
        }
        if (term.getRelatedMatch() != null) {
            this.relatedMatch = new HashSet<>(term.getRelatedMatch());
        }
        if (term.getExactMatchTerms() != null) {
            this.exactMatchTerms = new HashSet<>(term.getExactMatchTerms());
        }
        if (term.getNotations() != null) {
            this.notations = new HashSet<>(term.getNotations());
        }
        if (term.getExamples() != null) {
            this.examples = new HashSet<>(term.getExamples());
        }
        if (term.getProperties() != null) {
            this.properties = new HashMap<>();
            term.getProperties().keySet().stream()
                .filter(propertiesToExport::contains)
                .forEach(property -> this.properties.put(property, term.getProperties().get(property)));
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

    public Set<TermInfo> getExactMatchTerms() {
        return exactMatchTerms;
    }

    public void setExactMatchTerms(Set<TermInfo> exactMatchTerms) {
        this.exactMatchTerms = exactMatchTerms;
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

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(final Map<String, Set<String>> properties) {
        this.properties = properties;
    }
}

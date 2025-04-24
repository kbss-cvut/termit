/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Types;
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

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_stav_pojmu)
    private URI state;

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

    public URI getState() {
        return state;
    }

    public void setState(URI state) {
        this.state = state;
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
        if (!(o instanceof FacetedSearchResult that)) {
            return false;
        }
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

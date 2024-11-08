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
package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

@SparqlResultSetMapping(name = "FullTextSearchResult",
                        classes = {@ConstructorResult(targetClass = FullTextSearchResult.class,
                                                      variables = {
                                                              @VariableResult(name = "entity", type = URI.class),
                                                              @VariableResult(name = "label", type = String.class),
                                                              @VariableResult(name = "description", type = String.class),
                                                              @VariableResult(name = "vocabularyUri", type = URI.class),
                                                              @VariableResult(name = "state", type = URI.class),
                                                              @VariableResult(name = "type", type = String.class),
                                                              @VariableResult(name = "snippetField",
                                                                              type = String.class),
                                                              @VariableResult(name = "snippetText",
                                                                              type = String.class),
                                                              @VariableResult(name = "score", type = Double.class)
                                                      })})
public class FullTextSearchResult implements HasIdentifier, HasTypes, Serializable {

    @Id
    private URI uri;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String label;

    // This could be term definition, scope note or vocabulary description
    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @OWLDataProperty(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/fts/snippet-text")
    private String snippetText;

    @OWLDataProperty(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/fts/snippet-field")
    private String snippetField;

    @OWLDataProperty(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/fts/score")
    private Double score;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_stav_pojmu)
    private URI state;

    @Types
    private Set<String> types;

    public FullTextSearchResult() {
    }

    public FullTextSearchResult(URI uri, String label, String description, URI vocabulary, URI state, String type,
                                String snippetField, String snippetText, Double score) {
        this.uri = uri;
        this.label = label;
        this.description = description;
        this.vocabulary = vocabulary;
        this.state = state;
        this.types = Collections.singleton(type);
        this.snippetField = snippetField;
        this.snippetText = snippetText;
        this.score = score;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public String getSnippetText() {
        return snippetText;
    }

    public void setSnippetText(String snippetText) {
        this.snippetText = snippetText;
    }

    public String getSnippetField() {
        return snippetField;
    }

    public void setSnippetField(String snippetField) {
        this.snippetField = snippetField;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "FullTextSearchResult{" +
                "uri=" + uri +
                ", label='" + label + '\'' +
                ", vocabulary=" + vocabulary +
                ", types=" + types +
                ", snippetText='" + snippetText + '\'' +
                ", snippetField='" + snippetField + '\'' +
                ", score=" + score +
                '}';
    }
}


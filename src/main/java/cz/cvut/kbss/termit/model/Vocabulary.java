/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.resource.Document;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Audited
@OWLClass(iri = cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik)
@JsonLdAttributeOrder({"uri", "label", "description"})
public class Vocabulary extends Asset<String> implements Serializable {

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private String label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private String description;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_popisuje_dokument, cascade = { CascadeType.PERSIST }, fetch = FetchType.EAGER)
    private Document document;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar, cascade = { CascadeType.PERSIST, CascadeType.REMOVE },
            fetch = FetchType.EAGER)
    private Glossary glossary;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_model, cascade = { CascadeType.PERSIST, CascadeType.REMOVE },
            fetch = FetchType.EAGER)
    private Model model;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik, fetch = FetchType.EAGER)
    private Set<URI> importedVocabularies;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Glossary getGlossary() {
        return glossary;
    }

    public void setGlossary(Glossary glossary) {
        this.glossary = glossary;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public Set<URI> getImportedVocabularies() {
        return importedVocabularies;
    }

    public void setImportedVocabularies(Set<URI> importedVocabularies) {
        this.importedVocabularies = importedVocabularies;
    }

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<String>> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vocabulary)) {
            return false;
        }
        Vocabulary that = (Vocabulary) o;
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }

    @Override
    public String toString() {
        return "Vocabulary{" +
                getLabel() +
                " <" + getUri() + '>' +
                ", glossary=" + glossary +
                (importedVocabularies != null ?
                 ", importedVocabularies = [" + importedVocabularies.stream().map(p -> "<" + p + ">").collect(
                         Collectors.joining(", ")) + "]" : "") +
                '}';
    }
}

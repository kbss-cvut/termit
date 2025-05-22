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
package cz.cvut.kbss.termit.dto.listing;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.AbstractEntity;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@NonEntity
@OWLClass(iri = cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik)
@JsonLdAttributeOrder({"uri", "label", "description"})
public class VocabularyDto extends AbstractEntity {

    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = DC.Terms.DESCRIPTION)
    private MultilingualString description;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik)
    private Set<URI> importedVocabularies;

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_popisuje_dokument)
    private DocumentDto document;

    @Types
    private Set<String> types;

    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public Set<URI> getImportedVocabularies() {
        return importedVocabularies;
    }

    public void setImportedVocabularies(Set<URI> importedVocabularies) {
        this.importedVocabularies = importedVocabularies;
    }

    public DocumentDto getDocument() {
        return document;
    }

    public void setDocument(DocumentDto document) {
        this.document = document;
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
        if (!(o instanceof VocabularyDto that)) {
            return false;
        }
        return Objects.equals(getUri(), that.getUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri());
    }
}

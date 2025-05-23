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
package cz.cvut.kbss.termit.model.resource;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.Inferred;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.util.SupportsStorage;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_soubor)
@JsonLdAttributeOrder({"uri", "label", "description"})
public class File extends Resource implements SupportsStorage {

    @JsonBackReference
    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_je_casti_dokumentu, fetch = FetchType.EAGER)
    private Document document;

    @OWLAnnotationProperty(iri = DC.Terms.LANGUAGE, simpleLiteral = true)
    private String language;

    @Types
    private Set<String> types;

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
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
        if (!(o instanceof File file)) {
            return false;
        }
        return Objects.equals(getUri(), file.getUri());
    }

    @Override
    public String toString() {
        return "File{" +
                super.toString() + (language != null ? "@" + language : "") +
                (document != null ? "document=<" + document.getUri() + ">" : "") + '}';
    }

    /**
     * Resolves the name of the directory corresponding to this file.
     * <p>
     * Note that two modes of operation exists for this method:
     * <ul>
     * <li>If parent document exists, the document's directory is returned</li>
     * <li>Otherwise, directory is resolved based on this file's label</li>
     * </ul>
     *
     * @return Name of the directory storing this file
     */
    @JsonIgnore
    @Override
    public String getDirectoryName() {
        if (document != null) {
            return document.getDirectoryName();
        } else {
            if (getLabel() == null || getUri() == null) {
                throw new IllegalStateException("Missing file name or URI required for directory name resolution.");
            }
            final int dotIndex = getLabel().indexOf('.');
            final String labelPart = dotIndex > 0 ? getLabel().substring(0, getLabel().indexOf('.')) : getLabel();
            return IdentifierResolver.normalizeToAscii(labelPart) + '_' + getUri().hashCode();
        }
    }
}

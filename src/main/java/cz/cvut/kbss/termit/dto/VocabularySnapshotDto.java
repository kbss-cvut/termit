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
package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;

import java.net.URI;

/**
 * DTO for retrieving a single vocabulary snapshot from the backend.
 * <p>
 * Extends {@link VocabularyDto} with information about the snapshot author.
 */
@NonEntity
@OWLClass(iri = cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku)
@JsonLdAttributeOrder({"uri", "label", "description", "author"})
public class VocabularySnapshotDto extends VocabularyDto {

    @OWLObjectProperty(iri = DC.Terms.CREATOR)
    private UserAccount author;

    public VocabularySnapshotDto(Vocabulary source) {
        super(source);
        if (source.getProperties() != null && source.getProperties().containsKey(DC.Terms.CREATOR)) {
            Object authorValue = source.getProperties().get(DC.Terms.CREATOR).iterator().next();
            if (authorValue instanceof URI authorUri) {
                this.author = new UserAccount();
                this.author.setUri(authorUri);
            }
        }
    }

    public UserAccount getAuthor() {
        return author;
    }

    public void setAuthor(UserAccount author) {
        this.author = author;
    }
}


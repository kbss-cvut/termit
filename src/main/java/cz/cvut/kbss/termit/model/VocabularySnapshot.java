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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;

/**
 * Represents a snapshot of a vocabulary, which includes the author who created the snapshot.
 * <p>
 * The author is exposed as a separate field, allowing the frontend to display it as needed.
 */
@OWLClass(iri = cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku)
@JsonLdAttributeOrder({"uri", "label", "description", "author"})
public class VocabularySnapshot extends Vocabulary {

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_sioc_has_creator, fetch = FetchType.EAGER)
    private UserAccount author;

    public UserAccount getAuthor() {
        return author;
    }

    public void setAuthor(UserAccount author) {
        this.author = author;
    }
}

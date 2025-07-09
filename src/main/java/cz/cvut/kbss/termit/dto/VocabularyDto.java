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

import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessLevel;

/**
 * Used for retrieving a single {@link Vocabulary} from the backend.
 * <p>
 * Extends the data with information the level of access of the current user to this vocabulary.
 */
@NonEntity
public class VocabularyDto extends Vocabulary {

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    public VocabularyDto(Vocabulary source) {
        super(source.getUri());
        setLabel(source.getLabel());
        setDescription(source.getDescription());
        setPrimaryLanguage(source.getPrimaryLanguage());
        setGlossary(source.getGlossary());
        setModel(source.getModel());
        setDocument(source.getDocument());
        setImportedVocabularies(source.getImportedVocabularies());
        setProperties(source.getProperties());
        setTypes(source.getTypes());
        setAcl(source.getAcl());
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}

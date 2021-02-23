/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <https://www.gnu.org/licenses/>.
 */

package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;
import java.net.URI;

@OWLClass(iri = Vocabulary.s_c_uzivatelska_role)
public class UserRole implements HasIdentifier {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_prefLabel)
    private MultilingualString label;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_scopeNote)
    private MultilingualString description;

    @Override public URI getUri() {
        return uri;
    }

    @Override public void setUri(URI uri) {
        this.uri = uri;
    }

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
}

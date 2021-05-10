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
package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * Represents basic data about a {@link Term}.
 * <p>
 * This is not a full blown entity and should not be used to modify data.
 */
@OWLClass(iri = SKOS.CONCEPT)
public class TermInfo implements Serializable, HasIdentifier {

    @Id
    private URI uri;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = SKOS.PREF_LABEL)
    private MultilingualString label;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    public TermInfo() {
    }

    public TermInfo(URI uri) {
        this.uri = Objects.requireNonNull(uri);
    }

    public TermInfo(AbstractTerm term) {
        Objects.requireNonNull(term);
        this.uri = term.getUri();
        assert term.getLabel() != null;
        this.label = new MultilingualString(term.getLabel().getValue());
        this.vocabulary = term.getVocabulary();
    }

    public TermInfo(TermInfo other) {
        Objects.requireNonNull(other);
        this.uri = other.getUri();
        assert other.getLabel() != null;
        this.label = new MultilingualString(other.getLabel().getValue());
        this.vocabulary = other.getVocabulary();
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TermInfo)) {
            return false;
        }
        TermInfo termInfo = (TermInfo) o;
        return Objects.equals(uri, termInfo.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public String toString() {
        return "TermInfo{" + label + "<" + uri + ">" + '}';
    }
}

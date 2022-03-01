/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto.occurrence;

import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

abstract class AbstractTermOccurrenceInfo implements HasTypes {

    @OWLObjectProperty(iri = Vocabulary.s_p_je_prirazenim_termu)
    private URI term;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj)
    private URI resource;

    @Types
    private Set<String> types;

    public AbstractTermOccurrenceInfo() {
    }

    protected AbstractTermOccurrenceInfo(URI term, URI resource) {
        this.term = term;
        this.resource = resource;
    }

    public URI getTerm() {
        return term;
    }

    public void setTerm(URI term) {
        this.term = term;
    }

    public URI getResource() {
        return resource;
    }

    public void setResource(URI resource) {
        this.resource = resource;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractTermOccurrenceInfo)) {
            return false;
        }
        AbstractTermOccurrenceInfo that = (AbstractTermOccurrenceInfo) o;
        return Objects.equals(term, that.term) &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, resource, types);
    }

    @Override
    public String toString() {
        return "term=" + term +
                ", resource=" + resource +
                ", types=" + types;
    }
}

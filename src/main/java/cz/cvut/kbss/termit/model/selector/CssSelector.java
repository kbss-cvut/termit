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
package cz.cvut.kbss.termit.model.selector;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@OWLClass(iri = Vocabulary.s_c_selektor_css)
public class CssSelector extends Selector {

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = RDF.VALUE, simpleLiteral = true)
    private String value;

    public CssSelector() {
    }

    public CssSelector(@NotBlank String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public CssSelector copy() {
        return new CssSelector(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CssSelector that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "CssSelector{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}

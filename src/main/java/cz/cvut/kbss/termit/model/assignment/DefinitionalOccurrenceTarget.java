/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Target representing the definition of a term.
 * <p>
 * The {@link #getSource()} value points to the identifier of the term.
 */
@OWLClass(iri = Vocabulary.s_c_cil_definicniho_vyskytu)
public class DefinitionalOccurrenceTarget extends OccurrenceTarget {

    public DefinitionalOccurrenceTarget() {
    }

    public DefinitionalOccurrenceTarget(AbstractTerm source) {
        super(source);
    }

    @Override
    public String toString() {
        return "DefinitionalOccurrenceTarget{" + super.toString() + '}';
    }
}

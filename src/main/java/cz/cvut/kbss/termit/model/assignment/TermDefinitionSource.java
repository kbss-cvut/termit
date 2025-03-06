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
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Represents source of definition of a {@link cz.cvut.kbss.termit.model.Term} discovered in the content of a file.
 */
@OWLClass(iri = Vocabulary.s_c_zdroj_definice_termu)
public class TermDefinitionSource extends TermFileOccurrence {

    public TermDefinitionSource() {
    }

    public TermDefinitionSource(URI term, FileOccurrenceTarget target) {
        super(term, target);
    }

    @Override
    public TermDefinitionSource copy() {
        return new TermDefinitionSource(getTerm(), getTarget().copy());
    }
}

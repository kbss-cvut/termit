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
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.util.Copyable;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.stream.Collectors;

/**
 * Target representing the content of a file.
 * <p>
 * The {@link #getSource()} value points to the identifier of the file.
 */
@OWLClass(iri = Vocabulary.s_c_cil_souboroveho_vyskytu)
public class FileOccurrenceTarget extends OccurrenceTarget {

    public FileOccurrenceTarget() {
    }

    public FileOccurrenceTarget(File source) {
        super(source);
    }

    @Override
    public FileOccurrenceTarget copy() {
        final FileOccurrenceTarget copy = new FileOccurrenceTarget();
        copy.setSource(getSource());
        copy.setSelectors(Utils.emptyIfNull(getSelectors()).stream().map(Copyable::copy).collect(Collectors.toSet()));
        return copy;
    }

    @Override
    public String toString() {
        return "FileOccurrenceTarget{" + super.toString() + '}';
    }
}

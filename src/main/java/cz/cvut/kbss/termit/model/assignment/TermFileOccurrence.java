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
package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Represents an occurrence of a term in the content of a file.
 */
@OWLClass(iri = Vocabulary.s_c_souborovy_vyskyt_termu)
public class TermFileOccurrence extends TermOccurrence {

    public TermFileOccurrence() {
    }

    public TermFileOccurrence(URI term, FileOccurrenceTarget target) {
        super(term, target);
    }

    @Override
    public FileOccurrenceTarget getTarget() {
        assert target == null || target instanceof FileOccurrenceTarget;
        return (FileOccurrenceTarget) target;
    }

    public void setTarget(FileOccurrenceTarget target) {
        this.target = target;
    }

    @Override
    public TermFileOccurrence copy() {
        return new TermFileOccurrence(getTerm(), getTarget().copy());
    }
}

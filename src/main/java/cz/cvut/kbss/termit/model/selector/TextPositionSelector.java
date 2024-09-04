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
package cz.cvut.kbss.termit.model.selector;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Position-based selector of text in a document.
 * <p>
 * Note that position selector is susceptible to even minor changes in the document content (added or removed
 * whitespaces etc.).
 *
 * @see <a href="https://www.w3.org/TR/annotation-model/#text-position-selector">https://www.w3.org/TR/annotation-model/#text-position-selector</a>
 */
@OWLClass(iri = Vocabulary.s_c_selektor_pozici_v_textu)
public class TextPositionSelector extends Selector {

    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_startovni_pozici)
    private Integer start;

    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_koncovou_pozici)
    private Integer end;

    public TextPositionSelector() {
    }

    public TextPositionSelector(@NotNull Integer start, @NotNull Integer end) {
        this.start = start;
        this.end = end;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getEnd() {
        return end;
    }

    public void setEnd(Integer end) {
        this.end = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextPositionSelector selector)) {
            return false;
        }
        return Objects.equals(start, selector.start) &&
                Objects.equals(end, selector.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "TextPositionSelector{" +
                "start=" + start +
                ", end=" + end +
                "} " + super.toString();
    }
}

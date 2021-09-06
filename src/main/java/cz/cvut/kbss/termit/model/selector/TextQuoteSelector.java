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
package cz.cvut.kbss.termit.model.selector;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.util.Vocabulary;

import javax.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * Selector using text quote with prefix and suffix to identify the context.
 *
 * @see <a href="https://www.w3.org/TR/annotation-model/#text-quote-selector">https://www.w3.org/TR/annotation-model/#text-quote-selector</a>
 */
@OWLClass(iri = Vocabulary.s_c_selektor_text_quote)
public class TextQuoteSelector extends Selector {

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_presny_text_quote, simpleLiteral = true)
    private String exactMatch;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_prefix_text_quote, simpleLiteral = true)
    private String prefix;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_suffix_text_quote, simpleLiteral = true)
    private String suffix;

    public TextQuoteSelector() {
    }

    public TextQuoteSelector(@NotBlank String exactMatch) {
        this.exactMatch = exactMatch;
    }

    public String getExactMatch() {
        return exactMatch;
    }

    public void setExactMatch(String exactMatch) {
        this.exactMatch = exactMatch;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextQuoteSelector)) {
            return false;
        }
        TextQuoteSelector selector = (TextQuoteSelector) o;
        return Objects.equals(exactMatch, selector.exactMatch) &&
                Objects.equals(prefix, selector.prefix) &&
                Objects.equals(suffix, selector.suffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exactMatch, prefix, suffix);
    }

    @Override
    public String toString() {
        return "TextQuoteSelector{" +
                "exactMatch='" + exactMatch + '\'' +
                ", prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                "} " + super.toString();
    }
}

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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_zaznam_o_textove_analyze)
public class TextAnalysisRecord extends AbstractEntity {

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni)
    private Instant date;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_analyzovany_zdroj)
    private Resource analyzedResource;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_slovnik_pro_analyzu)
    private Set<URI> vocabularies;

    public TextAnalysisRecord() {
    }

    public TextAnalysisRecord(Instant date, Resource analyzedResource) {
        this.date = date;
        this.analyzedResource = analyzedResource;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Resource getAnalyzedResource() {
        return analyzedResource;
    }

    public void setAnalyzedResource(Resource analyzedResource) {
        this.analyzedResource = analyzedResource;
    }

    public Set<URI> getVocabularies() {
        return vocabularies;
    }

    public void setVocabularies(Set<URI> vocabularies) {
        this.vocabularies = vocabularies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextAnalysisRecord that)) {
            return false;
        }
        return Objects.equals(date, that.date) &&
                Objects.equals(analyzedResource, that.analyzedResource) &&
                Objects.equals(vocabularies, that.vocabularies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, analyzedResource, vocabularies);
    }

    @Override
    public String toString() {
        return "TextAnalysisRecord{" +
                "date=" + date +
                ",analyzedResource=" + analyzedResource +
                ",vocabularies=" + vocabularies +
                "}";
    }
}

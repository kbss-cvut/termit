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
package cz.cvut.kbss.termit.dto.statistics;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.ArrayList;
import java.util.List;

/**
 * Distribution of types of terms in vocabularies.
 */
@NonEntity
@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/pojem/term-type-distribution")
public class TermTypeDistributionDto {

    @OWLObjectProperty(iri = DC.Terms.SUBJECT)
    private RdfsResource vocabulary;

    @OWLObjectProperty(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/pojem/has-type-distribution")
    private List<DistributionDto> typeDistribution = new ArrayList<>();

    public RdfsResource getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(RdfsResource vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<DistributionDto> getTypeDistribution() {
        return typeDistribution;
    }

    public void setTypeDistribution(List<DistributionDto> typeDistribution) {
        this.typeDistribution = typeDistribution;
    }

    @Override
    public String toString() {
        return "TermTypeDistributionDto{" +
                "vocabulary=" + vocabulary +
                ", typeDistribution=" + typeDistribution +
                '}';
    }
}

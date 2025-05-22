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
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Represents the distribution of items w.r.t. a resource (e.g., vocabulary).
 */
@NonEntity
@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/distribution")
public class DistributionDto {

    @OWLObjectProperty(iri = DC.Terms.SUBJECT)
    private RdfsResource resource;

    @OWLDataProperty(iri = Vocabulary.s_p_as_totalItems)
    private Integer count;

    public DistributionDto() {
    }

    public DistributionDto(RdfsResource resource, Integer count) {
        this.resource = resource;
        this.count = count;
    }

    public RdfsResource getResource() {
        return resource;
    }

    public void setResource(RdfsResource resource) {
        this.resource = resource;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "FrequencyDto{" + resource + ": " + count + '}';
    }
}

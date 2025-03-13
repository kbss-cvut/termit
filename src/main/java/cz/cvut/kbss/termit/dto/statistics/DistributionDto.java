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

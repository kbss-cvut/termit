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

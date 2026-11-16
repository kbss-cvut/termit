package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.vocabulary.SKOS;

/**
 * Allows JOPA entity binding for collection DTO, otherwise JOPA would not bind ordered collection which is missing
 * explicit SKOS Collection type.
 */
@OWLClass(iri = SKOS.ORDERED_COLLECTION)
public class OrderedSkosCollectionDto extends SkosCollectionDto {
    @Override
    public boolean isOrdered() {
        return true;
    }
}

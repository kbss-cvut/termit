package cz.cvut.kbss.termit.dto.listing;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Vocabulary;

@NonEntity
@OWLClass(iri = Vocabulary.s_c_dokument)
@JsonLdAttributeOrder({"uri", "label", "description", "files"})
public class DocumentDto extends Resource {
}

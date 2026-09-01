package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;

import java.io.Serializable;
import java.net.URI;

/**
 * Object holding a basic description of a Term
 */
@JsonLdAttributeOrder({"uri", "label", "vocabulary", "state"})
public interface TermDescription extends Serializable, HasIdentifier, HasTypes {
    MultilingualString getLabel();
    URI getVocabulary();
    URI getState();
}

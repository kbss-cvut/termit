package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessLevel;

/**
 * Used for retrieving a single {@link Vocabulary} from the backend.
 * <p>
 * Extends the data with information the level of access of the current user to this vocabulary.
 */
@NonEntity
public class VocabularyDto extends Vocabulary {

    @OWLObjectProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    public VocabularyDto(Vocabulary source) {
        super(source.getUri());
        setLabel(source.getLabel());
        setDescription(source.getDescription());
        setGlossary(source.getGlossary());
        setModel(source.getModel());
        setDocument(source.getDocument());
        setImportedVocabularies(source.getImportedVocabularies());
        setProperties(source.getProperties());
        setTypes(source.getTypes());
        setAcl(source.getAcl());
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}

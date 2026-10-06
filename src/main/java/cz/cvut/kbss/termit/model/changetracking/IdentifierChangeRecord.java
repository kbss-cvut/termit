package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

// TODO: add entity class to IriMigrationChangeRecord
public class IdentifierChangeRecord extends AbstractChangeRecord {

    // TODO: entity class must be in the domain of has_original_value or the property must be changed
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_original_value)
    private URI originalIdentifier;

    public IdentifierChangeRecord() {
    }

    public URI getOriginalIdentifier() {
        return originalIdentifier;
    }

    public void setOriginalIdentifier(URI originalIdentifier) {
        this.originalIdentifier = originalIdentifier;
    }
}

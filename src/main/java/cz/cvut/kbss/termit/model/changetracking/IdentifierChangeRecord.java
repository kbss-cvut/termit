package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

@OWLClass(iri = Vocabulary.s_c_identifier_change)
public class IdentifierChangeRecord extends AbstractChangeRecord {

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_original_value)
    private URI originalIdentifier;

    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_new_value)
    private URI newIdentifier;

    public IdentifierChangeRecord() {}

    public URI getOriginalIdentifier() {
        return originalIdentifier;
    }

    public void setOriginalIdentifier(URI originalIdentifier) {
        this.originalIdentifier = originalIdentifier;
    }

    public URI getNewIdentifier() {
        return newIdentifier;
    }

    public void setNewIdentifier(URI newIdentifier) {
        this.newIdentifier = newIdentifier;
    }
}

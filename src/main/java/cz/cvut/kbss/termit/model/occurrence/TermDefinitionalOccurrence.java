package cz.cvut.kbss.termit.model.occurrence;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Represents an occurrence of a term in the definition of another term.
 */
@OWLClass(iri = Vocabulary.s_c_definicni_vyskyt_termu)
public class TermDefinitionalOccurrence extends TermOccurrence {

    public TermDefinitionalOccurrence() {
    }

    public TermDefinitionalOccurrence(URI term, DefinitionalOccurrenceTarget target) {
        super(term, target);
    }

    @Override
    public DefinitionalOccurrenceTarget getTarget() {
        assert target == null || target instanceof DefinitionalOccurrenceTarget;
        return (DefinitionalOccurrenceTarget) target;
    }

    public void setTarget(DefinitionalOccurrenceTarget target) {
        this.target = target;
    }
}

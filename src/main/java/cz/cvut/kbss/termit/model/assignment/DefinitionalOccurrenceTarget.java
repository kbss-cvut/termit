package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Target representing the definition of a term.
 * <p>
 * The {@link #getSource()} value points to the identifier of the term.
 */
@OWLClass(iri = Vocabulary.s_c_cil_definicniho_vyskytu)
public class DefinitionalOccurrenceTarget extends OccurrenceTarget {

    public DefinitionalOccurrenceTarget() {
    }

    public DefinitionalOccurrenceTarget(Term source) {
        super(source);
    }

    @Override
    public String toString() {
        return "DefinitionalOccurrenceTarget{" + super.toString() + '}';
    }
}

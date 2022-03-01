package cz.cvut.kbss.termit.model.occurrence;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Target representing the content of a file.
 * <p>
 * The {@link #getSource()} value points to the identifier of the file.
 */
@OWLClass(iri = Vocabulary.s_c_cil_souboroveho_vyskytu)
public class FileOccurrenceTarget extends OccurrenceTarget {

    public FileOccurrenceTarget() {
    }

    public FileOccurrenceTarget(File source) {
        super(source);
    }

    @Override
    public String toString() {
        return "FileOccurrenceTarget{" + super.toString() + '}';
    }
}

package cz.cvut.kbss.termit.model.occurrence;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Represents an occurrence of a term in the content of a file.
 */
@OWLClass(iri = Vocabulary.s_c_souborovy_vyskyt_termu)
public class TermFileOccurrence extends TermOccurrence {

    public TermFileOccurrence() {
    }

    public TermFileOccurrence(URI term, FileOccurrenceTarget target) {
        super(term, target);
    }

    @Override
    public FileOccurrenceTarget getTarget() {
        assert target == null || target instanceof FileOccurrenceTarget;
        return (FileOccurrenceTarget) target;
    }

    public void setTarget(FileOccurrenceTarget target) {
        this.target = target;
    }
}

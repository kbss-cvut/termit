package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Represents source of definition of a {@link cz.cvut.kbss.termit.model.Term} discovered in the content of a file.
 */
@OWLClass(iri = Vocabulary.s_c_zdroj_definice_termu)
public class TermDefinitionSource extends TermFileOccurrence {

    public TermDefinitionSource() {
    }

    public TermDefinitionSource(URI term, FileOccurrenceTarget target) {
        super(term, target);
    }
}

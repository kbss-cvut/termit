package cz.cvut.kbss.termit.model.util;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;

/**
 * Implementation of the Visitor pattern for assets recognized by TermIt.
 */
public interface AssetVisitor {

    void visitTerm(AbstractTerm term);

    void visitVocabulary(Vocabulary vocabulary);

    void visitResources(Resource resource);
}

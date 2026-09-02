package cz.cvut.kbss.termit.service.repository.term_removal;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;

/**
 * Applier performing the given operation described by the parameters.
 */
public interface TermParamsApplier {
    /**
     * Applies the operation described by the parameters.
     *
     * @param removalParams      Params describing how the term should be removed.
     * @param vocabulary         Vocabulary of the {@code term}
     * @param repositoryService  Service for interacting with the term repository
     */
    void apply(TermRemovalParams removalParams, Vocabulary vocabulary, TermRepositoryService repositoryService);
}

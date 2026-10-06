package cz.cvut.kbss.termit.service.repository.removal;

import cz.cvut.kbss.termit.service.repository.TermRepositoryService;

/**
 * Removes terms and executes any necessary related operations.
 */
public interface TermRemovalParamsApplier {
    /**
     * Removes a term, performing additional operations based on the specified parameters.
     *
     * @param removalParams      Params describing how the term should be removed.
     * @param repositoryService  Service for interacting with the term repository
     */
    void apply(TermRemovalParams removalParams, TermRepositoryService repositoryService);
}

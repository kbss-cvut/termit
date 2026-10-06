package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.event.BeforeAssetDeleteEvent;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Removes occurrences of deleted terms after their removal transaction has committed. */
@Service
public class TermOccurrenceCleanupListener {

    private final TermOccurrenceService termOccurrenceService;

    /**
     * Creates a listener which delegates cleanup to the transactional occurrence service.
     *
     * @param termOccurrenceService Service removing occurrences in an independent transaction
     */
    public TermOccurrenceCleanupListener(TermOccurrenceService termOccurrenceService) {
        this.termOccurrenceService = termOccurrenceService;
    }

    /**
     * Cleans up occurrences asynchronously after the term removal transaction commits.
     *
     * <p>Rolled-back transactions do not trigger this listener. Failed cleanup is retried up to three times, with a
     * one-second delay and an independent transaction for each attempt. Exhausted failures propagate to the
     * application's asynchronous exception handler.
     *
     * @param event Event containing the removed term
     */
    @Async
    @Retryable(maxRetries = 3)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTermRemoved(BeforeAssetDeleteEvent event) {
        if (event.getAsset() instanceof AbstractTerm term) {
            termOccurrenceService.removeAllOf(term);
        }
    }
}

package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.assignment.TermOccurrence;

import java.net.URI;

public interface TermOccurrenceService {

    /**
     * Persists the specified term occurrence.
     *
     * @param occurrence The occurrence to save
     */
    void persistOccurrence(TermOccurrence occurrence);

    /**
     * Approves the (possibly) suggested term occurrence with the specified identifier.
     * <p>
     * Note that it is possible that the occurrence does not need approving (it might have been created by the user).
     * The gist is that after calling this method, the occurrence is not suggested.
     *
     * @param identifier Identifier of the occurrence to approve
     */
    void approveOccurrence(URI identifier);

    /**
     * Removes the given occurrence.
     *
     * @param occurrence The occurrence to remove
     */
    void removeOccurrence(TermOccurrence occurrence);

    /**
     * Gets reference to a term occurrence given its URI.
     *
     * @param uri The URI of the occurrence to remove
     */
    TermOccurrence getRequiredReference(URI uri);
}

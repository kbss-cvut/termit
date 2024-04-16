package cz.cvut.kbss.termit.service.document;

import java.time.Instant;
import java.util.Optional;

/**
 * Specifies what resource content should be retrieved.
 *
 * @param at                            Timestamp indicating the version of the resource to retrieve
 * @param withoutUnconfirmedOccurrences Whether the content should not contain unconfirmed term occurrences
 */
public record ResourceRetrievalSpecification(Optional<Instant> at, boolean withoutUnconfirmedOccurrences) {
}

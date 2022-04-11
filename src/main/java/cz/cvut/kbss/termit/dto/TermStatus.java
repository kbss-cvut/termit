package cz.cvut.kbss.termit.dto;

/**
 * Represents a simplified status of a {@link cz.cvut.kbss.termit.model.Term}.
 * <p>
 * Since currently a Term has only a boolean marker indicating whether it is a draft or not, this enumeration represents
 * the true/false values of the marker. Its main purpose is to be used in a REST API endpoint used to update the draft
 * status of a term.
 */
public enum TermStatus {
    DRAFT, CONFIRMED
}

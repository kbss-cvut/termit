package cz.cvut.kbss.termit.rest.dto;

/**
 * Reason for saving a resource
 */
public enum ResourceSaveReason {
    NEW_OCCURRENCE,
    REMOVE_OCCURRENCE,
    OCCURRENCE_STATE_CHANGE,
    CREATE_FILE,
    REUPLOAD,
    UNKNOWN
}

package cz.cvut.kbss.termit.service.business.util;

import org.springframework.data.domain.Pageable;

public record TermSelectionParams(boolean flat, boolean full, boolean includeImported, boolean includeRelated,
                                  Pageable pageSpec, String language) {

    public TermSelectionParams(Pageable pageSpec) {
        this(false, false, false, false, pageSpec, null);
    }

    public TermSelectionParams(boolean flat, boolean full, boolean includeImported, boolean includeRelated, Pageable pageSpec) {
        this(flat, full, includeImported, includeRelated, pageSpec, null);
    }

    public TermSelectionParams withNotFull() {
        return new TermSelectionParams(flat, false, includeImported, includeRelated, pageSpec, language);
    }
}

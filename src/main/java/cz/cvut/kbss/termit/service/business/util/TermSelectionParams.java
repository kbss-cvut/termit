package cz.cvut.kbss.termit.service.business.util;

import org.springframework.data.domain.Pageable;

public record TermSelectionParams(boolean flat, boolean full, boolean includeImported, Pageable pageSpec) {

    public TermSelectionParams(Pageable pageSpec) {
        this(false, false, false, pageSpec);
    }

    public TermSelectionParams withNotFull() {
        return new TermSelectionParams(flat, false, includeImported, pageSpec);
    }
}

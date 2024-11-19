package cz.cvut.kbss.termit.exception;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.resource.File;

/**
 * Indicates that a language is not supported by the text analysis service.
 */
public class UnsupportedTextAnalysisLanguageException extends TermItException {

    public UnsupportedTextAnalysisLanguageException(String message, Asset<?> asset) {
        super(message, asset instanceof File ? "error.annotation.file.unsupportedLanguage" : "error.annotation.term.unsupportedLanguage");
    }
}

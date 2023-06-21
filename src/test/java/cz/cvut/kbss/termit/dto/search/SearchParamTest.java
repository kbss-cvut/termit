package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchParamTest {

    @Test
    void validateThrowsValidationExceptionWhenMatchTypeIsSubStringAndParamsHasMultipleValues() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(SKOS.NOTATION));
        sut.setValue(Set.of("one", "two", "three"));
        sut.setMatchType(MatchType.SUBSTRING);
        assertThrows(ValidationException.class, sut::validate);
    }

    @Test
    void validateThrowsValidationExceptionWhenMatchTypeIsExactMatchAndParamsHasMultipleValues() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(SKOS.NOTATION));
        sut.setValue(Set.of("one", "two"));
        sut.setMatchType(MatchType.EXACT_MATCH);
        assertThrows(ValidationException.class, sut::validate);
    }

    @Test
    void validateThrowsValidationExceptionWhenNoValueIsProvided() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(RDF.TYPE));
        sut.setMatchType(MatchType.IRI);
        assertThrows(ValidationException.class, sut::validate);
    }
}

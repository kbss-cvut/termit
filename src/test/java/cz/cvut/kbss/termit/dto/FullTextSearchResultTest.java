package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FullTextSearchResultTest {

    @Test
    void constructorSetsDraftToTrueWhenDraftArgumentIsNull() {
        final FullTextSearchResult sut = new FullTextSearchResult(Generator.generateUri(), "test",
                                                                  Generator.generateUri(), null,
                                                                  SKOS.CONCEPT, "label", "test text", 1.0);
        assertTrue(sut.isDraft());
    }
}

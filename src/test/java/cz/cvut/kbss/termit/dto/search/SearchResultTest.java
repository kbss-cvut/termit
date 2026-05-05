package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultTest {

    @Test
    void constructorSeparatesGroupConcatenatedLabelsIntoMultilingualString() {
        final SearchResult sut = new SearchResult(Generator.generateUri(),
                                                  generateGroupConcatenatedString("Label"), "Test",
                                                  Generator.generateUri(), Generator.generateUri(),
                                                  SKOS.CONCEPT, "prefLabel", "<em>Label</em>", 3.1419);
        assertEquals("Label - Czech", sut.getLabel().get("cs"));
        assertEquals("Label - English", sut.getLabel().get("en"));
        assertEquals("Label - German", sut.getLabel().get("de"));
    }

    private static String generateGroupConcatenatedString(String base) {
        return base + " - Czech" + "@cs" + Constants.GROUP_CONCAT_SEPARATOR +
                base + " - English" + "@en" + Constants.GROUP_CONCAT_SEPARATOR +
                base + " - German" + "@de";
    }

    @Test
    void constructorSeparatesGroupConcatenatedDescriptionsIntoMultilingualString() {
        final SearchResult sut = new SearchResult(Generator.generateUri(), "Test",
                                                  generateGroupConcatenatedString("Description"),
                                                  Generator.generateUri(), Generator.generateUri(),
                                                  SKOS.CONCEPT, "definition", "<em>Label</em>", 3.1419);
        assertEquals("Description - Czech", sut.getDescription().get("cs"));
        assertEquals("Description - English", sut.getDescription().get("en"));
        assertEquals("Description - German", sut.getDescription().get("de"));
    }

}

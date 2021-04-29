package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SparqlResultToTermInfoMapperTest {

    private final SparqlResultToTermInfoMapper sut = new SparqlResultToTermInfoMapper();

    @Test
    void mapsResultsWithSingleLabelToTermInfoInstances() {
        final List<Object[]> toMap = Arrays.asList(new Object[]{
                Generator.generateUri(),
                new LangString("Test one", Constants.DEFAULT_LANGUAGE),
                Generator.generateUri()
        }, new Object[]{
                Generator.generateUri(),
                new LangString("Test two", Constants.DEFAULT_LANGUAGE),
                Generator.generateUri()
        });
        final List<TermInfo> result = sut.map(toMap);
        assertEquals(toMap.size(), result.size());
        for (int i = 0; i < toMap.size(); i++) {
            final TermInfo ti = result.get(i);
            final Object[] input = toMap.get(i);
            assertEquals(input[0], ti.getUri());
            assertEquals(((LangString) input[1]).getValue(), ti.getLabel().get());
            assertEquals(input[2], ti.getVocabulary());
        }
    }

    @Test
    void mapsResultsWithMultilingualLabelToTermInfoInstances() {
        final URI tOneUri = Generator.generateUri();
        final URI vocUri = Generator.generateUri();
        final URI tTwoUri = Generator.generateUri();
        final List<Object[]> toMap = Arrays.asList(new Object[]{
                tOneUri,
                new LangString("Test one", Constants.DEFAULT_LANGUAGE),
                vocUri
        }, new Object[]{
                tOneUri,
                new LangString("Test jedna", "cs"),
                vocUri
        }, new Object[]{
                tTwoUri,
                new LangString("Test two", Constants.DEFAULT_LANGUAGE),
                vocUri
        }, new Object[]{
                tTwoUri,
                new LangString("Test dva", "cs"),
                vocUri
        });

        final List<TermInfo> result = sut.map(toMap);
        assertEquals(2, result.size());
        assertEquals("Test one", result.get(0).getLabel().get(Constants.DEFAULT_LANGUAGE));
        assertEquals("Test jedna", result.get(0).getLabel().get("cs"));
        assertEquals("Test two", result.get(1).getLabel().get(Constants.DEFAULT_LANGUAGE));
        assertEquals("Test dva", result.get(1).getLabel().get("cs"));
        assertEquals(tOneUri, result.get(0).getUri());
        assertEquals(vocUri, result.get(0).getVocabulary());
        assertEquals(tTwoUri, result.get(1).getUri());
        assertEquals(vocUri, result.get(1).getVocabulary());
    }
}
/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparqlResultToTermInfoMapperTest {

    private final SparqlResultToTermInfoMapper sut = new SparqlResultToTermInfoMapper();

    @Test
    void mapsResultsWithSingleLabelToTermInfoInstances() {
        final List<Object[]> toMap = Arrays.asList(new Object[]{
                Generator.generateUri(),
                new LangString("Test one", Environment.LANGUAGE),
                Generator.generateUri()
        }, new Object[]{
                Generator.generateUri(),
                new LangString("Test two", Environment.LANGUAGE),
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
                new LangString("Test one", Environment.LANGUAGE),
                vocUri
        }, new Object[]{
                tOneUri,
                new LangString("Test jedna", "cs"),
                vocUri
        }, new Object[]{
                tTwoUri,
                new LangString("Test two", Environment.LANGUAGE),
                vocUri
        }, new Object[]{
                tTwoUri,
                new LangString("Test dva", "cs"),
                vocUri
        });

        final List<TermInfo> result = sut.map(toMap);
        assertEquals(2, result.size());
        assertEquals("Test one", result.get(0).getLabel().get(Environment.LANGUAGE));
        assertEquals("Test jedna", result.get(0).getLabel().get("cs"));
        assertEquals("Test two", result.get(1).getLabel().get(Environment.LANGUAGE));
        assertEquals("Test dva", result.get(1).getLabel().get("cs"));
        assertEquals(tOneUri, result.get(0).getUri());
        assertEquals(vocUri, result.get(0).getVocabulary());
        assertEquals(tTwoUri, result.get(1).getUri());
        assertEquals(vocUri, result.get(1).getVocabulary());
    }
}

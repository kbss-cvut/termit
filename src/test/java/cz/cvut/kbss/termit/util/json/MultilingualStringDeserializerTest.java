/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.util.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import cz.cvut.kbss.jopa.model.MultilingualString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultilingualStringDeserializerTest {

    private final MultilingualStringDeserializer sut = new MultilingualStringDeserializer();

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        this.mapper = new ObjectMapper();
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(MultilingualString.class, sut);
        mapper.registerModule(module);
    }

    @Test
    void deserializeDeserializesMapOfTranslationsIntoMultilingualString() throws Exception {
        final String input = "{" +
                "\"en\": \"building\"," +
                "\"cs\": \"budova\"" +
                "}";
        final MultilingualString result = mapper.readValue(input, MultilingualString.class);
        assertNotNull(result);
        assertEquals("building", result.get("en"));
        assertEquals("budova", result.get("cs"));
    }

    @Test
    void deserializeInterpretsValueWithEmptyKeyAsSimpleLiteral() throws Exception {
        final String input = "{" +
                "\"en\": \"building\"," +
                "\"\": \"budova\"" +
                "}";
        final MultilingualString result = mapper.readValue(input, MultilingualString.class);
        assertNotNull(result);
        assertEquals("building", result.get("en"));
        assertEquals("budova", result.get());
    }
}

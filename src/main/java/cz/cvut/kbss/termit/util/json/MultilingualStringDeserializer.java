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
package cz.cvut.kbss.termit.util.json;

import cz.cvut.kbss.jopa.model.MultilingualString;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.Map;

public class MultilingualStringDeserializer extends StdDeserializer<MultilingualString> {

    public MultilingualStringDeserializer() {
        super(MultilingualString.class);
    }

    @Override
    public MultilingualString deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws JacksonException {
        final JsonNode node = jsonParser.objectReadContext().readTree(jsonParser);
        final MultilingualString result = new MultilingualString();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (field.getKey().isEmpty()) {
                result.set(field.getValue().asString());
            } else {
                result.set(field.getKey(), field.getValue().asString());
            }
        }
        return result;
    }
}

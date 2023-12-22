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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import cz.cvut.kbss.jopa.model.MultilingualString;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class MultilingualStringDeserializer extends StdDeserializer<MultilingualString> {

    public MultilingualStringDeserializer() {
        super(MultilingualString.class);
    }

    @Override
    public MultilingualString deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final MultilingualString result = new MultilingualString();
        final Iterator<Map.Entry<String, JsonNode>> fieldIterator = node.fields();
        while (fieldIterator.hasNext()) {
            final Map.Entry<String, JsonNode> field = fieldIterator.next();
            if (field.getKey().isEmpty()) {
                result.set(field.getValue().textValue());
            } else {
                result.set(field.getKey(), field.getValue().textValue());
            }
        }
        return result;
    }
}

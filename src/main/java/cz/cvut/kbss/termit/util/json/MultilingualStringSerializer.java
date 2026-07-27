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
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Map;
import java.util.Objects;

/**
 * Custom Jackson serializer for {@link MultilingualString} instances.
 * <p>
 * Needed by the JSON serialization.
 */
public class MultilingualStringSerializer extends StdSerializer<MultilingualString> {

    public MultilingualStringSerializer() {
        super(MultilingualString.class);
    }

    @Override
    public void serialize(MultilingualString multilingualString, JsonGenerator jsonGenerator,
                          SerializationContext ctxt) throws JacksonException {
        Objects.requireNonNull(multilingualString);
        jsonGenerator.writeStartObject();
        for (Map.Entry<String, String> entry : multilingualString.getValue().entrySet()) {
            jsonGenerator.writeStringProperty(entry.getKey() != null ? entry.getKey() : "", entry.getValue());
        }
        jsonGenerator.writeEndObject();
    }
}

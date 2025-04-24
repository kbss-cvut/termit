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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import cz.cvut.kbss.jopa.model.MultilingualString;

import java.io.IOException;
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
                          SerializerProvider serializerProvider) throws IOException {
        Objects.requireNonNull(multilingualString);
        jsonGenerator.writeStartObject();
        for (Map.Entry<String, String> entry : multilingualString.getValue().entrySet()) {
            jsonGenerator.writeStringField(entry.getKey() != null ? entry.getKey() : "", entry.getValue());
        }
        jsonGenerator.writeEndObject();
    }
}

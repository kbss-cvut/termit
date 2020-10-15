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

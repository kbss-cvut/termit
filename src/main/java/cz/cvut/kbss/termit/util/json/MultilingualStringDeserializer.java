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

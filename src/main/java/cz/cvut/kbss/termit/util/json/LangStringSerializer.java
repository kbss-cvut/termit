package cz.cvut.kbss.termit.util.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import cz.cvut.kbss.ontodriver.model.LangString;

import java.io.IOException;

public class LangStringSerializer extends StdSerializer<LangString> {

    public LangStringSerializer() {
        super(LangString.class);
    }

    @Override
    public void serialize(LangString langString, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(langString.toString());
    }
}

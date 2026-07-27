package cz.cvut.kbss.termit.util.json;

import cz.cvut.kbss.ontodriver.model.LangString;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class LangStringSerializer extends StdSerializer<LangString> {

    public LangStringSerializer() {
        super(LangString.class);
    }

    @Override
    public void serialize(LangString langString, JsonGenerator jsonGenerator, SerializationContext ctxt)
            throws JacksonException {
        jsonGenerator.writeString(langString.toString());
    }
}

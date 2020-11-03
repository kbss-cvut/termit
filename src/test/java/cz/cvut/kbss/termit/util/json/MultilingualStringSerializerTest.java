package cz.cvut.kbss.termit.util.json;

import com.fasterxml.jackson.core.JsonGenerator;
import cz.cvut.kbss.jopa.model.MultilingualString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.Mockito.verify;

class MultilingualStringSerializerTest {

    private final MultilingualStringSerializer sut = new MultilingualStringSerializer();

    @Mock
    private JsonGenerator jsonGenerator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void serializeWritesMultilingualStringAsMapOfLanguageToValuePairs() throws Exception {
        final MultilingualString value = MultilingualString.create("test", "en");
        value.set("cs", "test");
        sut.serialize(value, jsonGenerator, null);
        final InOrder inOrder = Mockito.inOrder(jsonGenerator);
        inOrder.verify(jsonGenerator).writeStartObject();
        value.getValue().forEach((lang, str) -> {
            try {
                inOrder.verify(jsonGenerator).writeStringField(lang, str);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        inOrder.verify(jsonGenerator).writeEndObject();
    }

    @Test
    void serializeWritesNullLanguageAsEmptyKey() throws Exception {
        final MultilingualString value = MultilingualString.create("test", "en");
        value.set("test");
        sut.serialize(value, jsonGenerator, null);
        verify(jsonGenerator).writeStringField("", value.get());
    }
}

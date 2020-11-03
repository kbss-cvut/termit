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

package cz.cvut.kbss.termit.service.validation;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class ExternalServiceValidatorTest {

    private static final String SERVICE_URL = "http://localhost/validation-service/validate";

    final RestTemplate restClient = new RestTemplate();

    private MockRestServiceServer mockServer;

    private ExternalServiceValidator sut;

    @BeforeEach
    void setUp() {
        this.mockServer = MockRestServiceServer.createServer(restClient);
        this.sut = new ExternalServiceValidator(restClient, SERVICE_URL);
    }

    @Test
    void validatePassesContextUrisAndLanguageAsRequestParamsToExternalService() {
        final List<URI> contexts = List.of(Generator.generateUri(), Generator.generateUri());
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.addAll("contextUri", contexts.stream().map(URI::toString).toList());
        params.addAll("rule", ExternalServiceValidator.VALIDATION_RULES);
        params.add("language", Environment.LANGUAGE);
        mockServer.expect(requestTo(SERVICE_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                  .andExpect(content().formData(params))
                  .andRespond(withSuccess("{\"conforms\": true}", MediaType.APPLICATION_JSON));
        sut.validate(contexts, Environment.LANGUAGE);
        mockServer.verify();
    }

    @Test
    void validateExtractsValidationResultAndReturnsIt() {
        final URI context = Generator.generateUri();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.addAll("contextUri", List.of(context.toString()));
        params.addAll("rule", ExternalServiceValidator.VALIDATION_RULES);
        params.add("language", Environment.LANGUAGE);
        mockServer.expect(requestTo(SERVICE_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                  .andExpect(content().formData(params))
                  .andRespond(withSuccess("""
                                                  {
                                                    "conforms": false,
                                                    "results": [
                                                      {
                                                        "severity": "http://www.w3.org/ns/shacl#Warning",
                                                        "message": {
                                                          "cs": "Pojem nemá zdroj.",
                                                          "en": "The term does not have a source."
                                                        },
                                                        "focusNode": "http://onto.fel.cvut.cz/ontologies/slovnik/test/term/test",
                                                        "sourceShape": "https://slovník.gov.cz/jazyk/obecný/g13"
                                                      }
                                                    ]
                                                  }
                                                  """, MediaType.APPLICATION_JSON));
        final List<ValidationResult> result = sut.validate(List.of(context), Environment.LANGUAGE);
        assertEquals(1, result.size());
        assertEquals(URI.create("http://www.w3.org/ns/shacl#Warning"), result.get(0).getSeverity());
        assertEquals(URI.create("http://onto.fel.cvut.cz/ontologies/slovnik/test/term/test"),
                     result.get(0).getTermUri());
        assertEquals("The term does not have a source.", result.get(0).getMessage().get("en"));
        assertEquals(URI.create("https://slovník.gov.cz/jazyk/obecný/g13"), result.get(0).getIssueCauseUri());
        mockServer.verify();
    }

    @Test
    void validateReturnsEmptyListWhenContextIsValid() {
        final URI context = Generator.generateUri();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.addAll("contextUri", List.of(context.toString()));
        params.addAll("rule", ExternalServiceValidator.VALIDATION_RULES);
        params.add("language", Environment.LANGUAGE);
        mockServer.expect(requestTo(SERVICE_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                  .andExpect(content().formData(params))
                  .andRespond(withSuccess("""
                                                  {
                                                    "conforms": true
                                                  }
                                                  """, MediaType.APPLICATION_JSON));
        final List<ValidationResult> result = sut.validate(List.of(context), Environment.LANGUAGE);
        assertTrue(result.isEmpty());
        mockServer.verify();
    }
}

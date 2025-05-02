package cz.cvut.kbss.termit.persistence.validation;

import com.github.sgov.server.ShaclSeverity;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates repository contexts by calling an external validation service.
 */
public class ExternalServiceValidator implements RepositoryContextValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalServiceValidator.class);

    private final RestTemplate restClient;

    private final String validationServiceUrl;

    public ExternalServiceValidator(RestTemplate restClient, String validationServiceUrl) {
        this.restClient = restClient;
        this.validationServiceUrl = validationServiceUrl;
    }

    @NotNull
    @Override
    public List<ValidationResult> validate(@NotNull List<URI> contexts, @NotNull String language) {
        Objects.requireNonNull(contexts);
        Objects.requireNonNull(language);
        assert validationServiceUrl != null && !validationServiceUrl.isBlank();
        LOG.debug("Invoking validation service for contexts {} and language '{}'.", contexts, language);

        final long start = System.currentTimeMillis();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.addAll("contextUri", contexts.stream().map(URI::toString).toList());
        params.add("language", language);
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            final ResponseEntity<ExternalValidationReport> resp = restClient.exchange(validationServiceUrl,
                                                                                      HttpMethod.POST,
                                                                                      new HttpEntity<>(params, headers),
                                                                                      ExternalValidationReport.class);
            final ExternalValidationReport report = resp.getBody();
            assert report != null;
            final long end = System.currentTimeMillis();
            LOG.debug("Validation finished in {}s. Valid? {}.", Utils.millisToString(end - start),
                      report.isConforms());
            return report.results.stream().sorted().map(r -> new ValidationResult()
                    .setTermUri(r.focusNode())
                    .setSeverity(r.severity())
                    .setMessage(new MultilingualString(r.message()))
                    .setIssueCauseUri(r.sourceShape())).toList();
        } catch (RestClientException e) {
            throw new WebServiceIntegrationException("Unable to invoke validation service.", e);
        }
    }

    public static class ExternalValidationReport {
        private boolean conforms;

        private List<ValidationResultItem> results = new ArrayList<>();

        public boolean isConforms() {
            return conforms;
        }

        public void setConforms(boolean conforms) {
            this.conforms = conforms;
        }

        public List<ValidationResultItem> getResults() {
            return results;
        }

        public void setResults(
                List<ValidationResultItem> results) {
            this.results = results;
        }
    }

    public record ValidationResultItem(URI severity, Map<String, String> message, URI focusNode, URI sourceShape)
            implements Comparable<ValidationResultItem> {
        @Override
        public int compareTo(@NotNull ExternalServiceValidator.ValidationResultItem o) {
            final Optional<ShaclSeverity> ownSeverity = Arrays.stream(ShaclSeverity.values())
                                                              .filter(ss -> ss.getUri().equals(severity.toString()))
                                                              .findFirst();
            final Optional<ShaclSeverity> otherSeverity = Arrays.stream(ShaclSeverity.values())
                                                                .filter(ss -> ss.getUri().equals(o.severity.toString()))
                                                                .findFirst();
            return ownSeverity.map(value -> otherSeverity.map(value::compareTo).orElse(-1))
                              .orElseGet(() -> otherSeverity.isPresent() ? 1 : 0);
        }
    }
}

/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/vocabularies")
public class VocabularyController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyController.class);

    private final VocabularyService vocabularyService;

    @Autowired
    public VocabularyController(VocabularyService vocabularyService, IdentifierResolver idResolver,
                                Configuration config) {
        super(idResolver, config);
        this.vocabularyService = vocabularyService;
    }

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<Vocabulary>> getAll(ServletWebRequest webRequest) {
        if (webRequest.checkNotModified(vocabularyService.getLastModified())) {
            return null;
        }
        return ResponseEntity.ok().lastModified(vocabularyService.getLastModified()).body(vocabularyService.findAll());
    }

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(@RequestBody Vocabulary vocabulary) {
        vocabularyService.persist(vocabulary);
        LOG.debug("Vocabulary {} created.", vocabulary);
        return ResponseEntity.created(generateLocation(vocabulary.getUri())).build();
    }

    @GetMapping(value = "/{fragment}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Vocabulary getById(@PathVariable String fragment,
                              @RequestParam(name = QueryParams.NAMESPACE,
                                            required = false) Optional<String> namespace) {
        final URI id = resolveVocabularyUri(fragment, namespace);
        return vocabularyService.findRequired(id);
    }

    /**
     * Gets imports (including transitive) of vocabulary with the specified identification
     */
    @GetMapping(value = "/{fragment}/imports", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getTransitiveImports(@PathVariable String fragment,
                                                @RequestParam(name = QueryParams.NAMESPACE,
                                                              required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getTransitivelyImportedVocabularies(vocabulary);
    }

    /**
     * Allows to import a vocabulary (or its  glossary) from the specified file.
     *
     * @param file   File containing data to import
     * @param rename true if the IRIs should be renamed
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(@RequestParam(name = "file") MultipartFile file,
                                                 @RequestParam(name = "rename") boolean rename) {
        final Vocabulary vocabulary = vocabularyService.importVocabulary(rename, file);
        LOG.debug("New vocabulary {} imported.", vocabulary);
        final URI location = generateLocation(vocabulary.getUri());
        final String adjustedLocation = location.toString().replace("/import/", "/");
        return ResponseEntity.created(URI.create(adjustedLocation)).build();
    }

    /**
     * Allows to import a SKOS glossary from the specified file.
     *
     * @param fragment  vocabulary name
     * @param namespace (optional) vocabulary namespace
     * @param file      File containing data to import
     */
    @PostMapping("/{fragment}/import")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createVocabulary(@PathVariable String fragment,
                                                 @RequestParam(name = QueryParams.NAMESPACE,
                                                               required = false) Optional<String> namespace,
                                                 @RequestParam(name = "file") MultipartFile file) {
        final URI vocabularyIri = resolveVocabularyUri(fragment, namespace);
        final Vocabulary vocabulary = vocabularyService.importVocabulary(vocabularyIri, file);
        LOG.debug("Vocabulary {} re-imported.", vocabulary);
        String location = generateLocation(vocabulary.getUri()).toString();
        location = location.replace("/import/" + fragment, "");
        return ResponseEntity.created(URI.create(location)).build();
    }

    private URI resolveVocabularyUri(String fragment, Optional<String> namespace) {
        return resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
    }

    /**
     * Gets the change history of a vocabulary with the specified identification
     */
    @GetMapping(value = "/{fragment}/history", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AbstractChangeRecord> getHistory(@PathVariable String fragment,
                                                 @RequestParam(name = QueryParams.NAMESPACE,
                                                               required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getChanges(vocabulary);
    }

    /**
     * Gets the change history of a vocabulary content with the specified identification
     */
    @GetMapping(value = "/{fragment}/history-of-content",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<AggregatedChangeInfo> getHistoryOfContent(@PathVariable String fragment,
                                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                                        required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getChangesOfContent(vocabulary);
    }

    @PutMapping(value = "/{fragment}", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void updateVocabulary(@PathVariable String fragment,
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace,
                                 @RequestBody Vocabulary update) {
        final URI vocabularyUri = resolveVocabularyUri(fragment, namespace);
        verifyRequestAndEntityIdentifier(update, vocabularyUri);
        vocabularyService.update(update);
        LOG.debug("Vocabulary {} updated.", update);
    }

    /**
     * Runs text analysis on definitions of all terms in vocabulary.
     * <p>
     * This is a legacy endpoint intended mainly for internal use/testing, since the analysis is executed automatically
     * when specific conditions are fulfilled.
     */
    @PutMapping(value = "/{vocabularyIdFragment}/terms/text-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void runTextAnalysisOnAllTerms(@PathVariable String vocabularyIdFragment,
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace) {
        vocabularyService.runTextAnalysisOnAllTerms(getById(vocabularyIdFragment, namespace));
    }

    /**
     * Runs text analysis on definitions of all terms in all vocabularies.
     * <p>
     * The text analysis invocation is asynchronous, so this method returns immediately after invoking the text analysis
     * with status {@link HttpStatus#ACCEPTED}.
     * <p>
     * This is a legacy endpoint intended mainly for internal use/testing, since the analysis is executed automatically
     * when specific conditions are fulfilled.
     */
    @GetMapping(value = "/text-analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void runTextAnalysisOnAllVocabularies() {
        vocabularyService.runTextAnalysisOnAllVocabularies();
    }

    /**
     * Removes a vocabulary.
     *
     * @param fragment  vocabulary name
     * @param namespace (optional) vocabulary namespace
     * @see VocabularyService#remove(Vocabulary)  for details.
     */
    @DeleteMapping(value = "/{fragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public void removeVocabulary(@PathVariable String fragment,
                                 @RequestParam(name = QueryParams.NAMESPACE,
                                               required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary toRemove = vocabularyService.getRequiredReference(identifier);
        vocabularyService.remove(toRemove);
        LOG.debug("Vocabulary {} removed.", toRemove);
    }

    /**
     * Validates a vocabulary.
     *
     * @param fragment  vocabulary name
     * @param namespace (optional) vocabulary namespace
     * @return list of validation outcomes
     */
    @PreAuthorize("permitAll()")
    @GetMapping(value = "/{fragment}/validate",
                produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<ValidationResult> validateVocabulary(@PathVariable String fragment,
                                                     @RequestParam(name = QueryParams.NAMESPACE,
                                                                   required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.validateContents(vocabulary);
    }

    @PostMapping("/{fragment}/versions")
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_FULL_USER + "')")
    public ResponseEntity<Void> createSnapshot(@PathVariable String fragment,
                                               @RequestParam(name = QueryParams.NAMESPACE,
                                                             required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.createSnapshot(vocabulary);
        LOG.debug("Created snapshot of vocabulary {}.", vocabulary);
        return ResponseEntity.created(generateLocation(vocabulary.getUri())).build();
    }

    @GetMapping("/{fragment}/versions")
    public ResponseEntity<?> getSnapshots(@PathVariable String fragment,
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @RequestParam(name = "at", required = false) Optional<String> at) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        if (at.isPresent()) {
            try {
                final Instant instant = Instant.parse(at.get());
                return ResponseEntity.ok(vocabularyService.findVersionValidAt(vocabulary, instant));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(at.get() + " is not a valid ISO-formatted timestamp.");
            }
        }
        return ResponseEntity.ok(vocabularyService.findSnapshots(vocabulary));
    }
}

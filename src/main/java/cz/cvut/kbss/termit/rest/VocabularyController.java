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
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.rest.util.RestUtils;
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
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
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
    public ResponseEntity<List<VocabularyDto>> getAll(ServletWebRequest webRequest) {
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

    @GetMapping(value = "/{fragment}/related", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public Collection<URI> getRelated(@PathVariable String fragment,
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(resolveVocabularyUri(fragment, namespace));
        return vocabularyService.getRelatedVocabularies(vocabulary);
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
        return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import")).build();
    }

    URI locationWithout(URI location, String toRemove) {
        return URI.create(location.toString().replace(toRemove, ""));
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
        return ResponseEntity.created(locationWithout(generateLocation(vocabulary.getUri()), "/import/" + fragment))
                             .build();
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
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
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
        final Snapshot snapshot = vocabularyService.createSnapshot(vocabulary);
        LOG.debug("Created snapshot of vocabulary {}.", vocabulary);
        return ResponseEntity.created(
                locationWithout(generateLocation(snapshot.getUri()), "/" + fragment + "/versions")).build();
    }

    @GetMapping(value = "/{fragment}/versions", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<?> getSnapshots(@PathVariable String fragment,
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @RequestParam(name = "at", required = false) Optional<String> at) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        if (at.isPresent()) {
            final Instant instant = RestUtils.parseTimestamp(at.get());
            return ResponseEntity.ok(vocabularyService.findVersionValidAt(vocabulary, instant));
        }
        return ResponseEntity.ok(vocabularyService.findSnapshots(vocabulary));
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @GetMapping(value = "/{fragment}/acl", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public AccessControlListDto getAccessControlList(@PathVariable String fragment,
                                                     @RequestParam(name = QueryParams.NAMESPACE,
                                                                   required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.getAccessControlList(vocabulary);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PostMapping(value = "/{fragment}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addAccessControlRecord(@PathVariable String fragment,
                                       @RequestParam(name = QueryParams.NAMESPACE,
                                                     required = false) Optional<String> namespace,
                                       @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.addAccessControlRecords(vocabulary, record);
        LOG.debug("Added access control record to ACL of vocabulary {}.", vocabulary);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{fragment}/acl/records", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAccessControlRecord(@PathVariable String fragment,
                                          @RequestParam(name = QueryParams.NAMESPACE,
                                                        required = false) Optional<String> namespace,
                                          @RequestBody AccessControlRecord<?> record) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.removeAccessControlRecord(vocabulary, record);
        LOG.debug("Removed access control record from ACL of vocabulary {}.", vocabulary);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PutMapping(value = "/{fragment}/acl/records/{recordId}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAccessControlLevel(@PathVariable String fragment, @PathVariable String recordId,
                                         @RequestParam(name = QueryParams.NAMESPACE,
                                                       required = false) Optional<String> namespace,
                                         @RequestBody AccessControlRecord<?> record) {
        if (!record.getUri().toString().contains(recordId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change record identifier does not match URL.");
        }
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        vocabularyService.updateAccessControlLevel(vocabulary, record);
        LOG.debug("Updated access control record {} from ACL of vocabulary {}.", record, vocabulary);
    }

    @GetMapping(value = "/{fragment}/access-level")
    public AccessLevel getAccessLevel(@PathVariable String fragment,
                                      @RequestParam(name = QueryParams.NAMESPACE,
                                                    required = false) Optional<String> namespace) {
        final URI identifier = resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
        final Vocabulary vocabulary = vocabularyService.getRequiredReference(identifier);
        return vocabularyService.getAccessLevel(vocabulary);
    }
}

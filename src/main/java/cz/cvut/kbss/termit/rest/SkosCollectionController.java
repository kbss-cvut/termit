package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.rest.doc.ApiDocConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SkosCollectionService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static cz.cvut.kbss.termit.rest.util.RestUtils.createPageRequest;

@Tag(name = "SKOS Collections", description = "SKOS collection management API")
@RestController
@RequestMapping(SkosCollectionController.PATH)
public class SkosCollectionController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(SkosCollectionController.class);

    public static final String PATH = "/vocabularies/{localName}/collections";

    public static final String COLLECTION_NAMESPACE_PARAM = "collectionNamespace";

    private final SkosCollectionService collectionService;

    public SkosCollectionController(
            IdentifierResolver idResolver, Configuration config, SkosCollectionService collectionService) {
        super(idResolver, config);
        this.collectionService = collectionService;
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Gets collections from the vocabulary with the specified identifier. "
                    + "Collections are ordered by their labels in the vocabulary primary language.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of collections in the vocabulary."),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found.")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<SkosCollectionDto>> getAll(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE_SIZE, required = false)
                    Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE, required = false)
                    Integer pageNo) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final Page<SkosCollectionDto> result =
                collectionService.findAll(vocabularyUri, createPageRequest(pageSize, pageNo));
        return ResponseEntity.ok()
                .header(Constants.X_TOTAL_COUNT_HEADER, Long.toString(result.getTotalElements()))
                .body(result.getContent());
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Creates a new unordered collection in the vocabulary with the specified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Collection created."),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found."),
        @ApiResponse(responseCode = "409", description = "Collection data invalid.")
    })
    @PostMapping(consumes = JsonLd.MEDIA_TYPE)
    public ResponseEntity<Void> create(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(description = "The new collection.") @RequestBody TermCollection collection) {
        if (collection.getMembers() == null) {
            collection.setMembers(Collections.emptySet());
        }
        return persistCollection(localName, namespace, collection);
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Creates a new ordered collection in the vocabulary with the specified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Collection created."),
        @ApiResponse(responseCode = "404", description = "Vocabulary not found."),
        @ApiResponse(responseCode = "409", description = "Collection data invalid.")
    })
    @PostMapping(value = "/ordered", consumes = JsonLd.MEDIA_TYPE)
    public ResponseEntity<Void> createOrdered(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(description = "The new collection.") @RequestBody OrderedTermCollection collection) {
        if (collection.getMembers() == null) {
            collection.setMembers(Collections.emptyList());
        }
        return persistCollection(localName, namespace, collection);
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description =
                    "Updates the collection with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Collection successfully updated."),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
        @ApiResponse(responseCode = "409", description = "Collection data invalid.")
    })
    @PutMapping(value = "/{collectionLocalName}", consumes = JsonLd.MEDIA_TYPE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace,
            @Parameter(description = "Updated collection data.") @RequestBody SkosCollectionDto collection) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        verifyRequestAndEntityIdentifier(collection, resolveIdentifier(collectionNamespace, collectionLocalName));
        ensureCollectionInVocabulary(collection, vocabularyUri);
        collectionService.update(collection);
        LOG.debug("Collection {} updated.", collection);
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description =
                    "Gets the collection with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collection detail."),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(
            value = "/{collectionLocalName}",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public SkosCollectionDto getById(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final URI collectionUri = resolveIdentifier(collectionNamespace, collectionLocalName);
        return collectionService.findRequiredDto(collectionUri, vocabularyUri);
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description =
                    "Removes the collection with the specified local name from the vocabulary with the specified identifier.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Collection successfully removed."),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @DeleteMapping(value = "/{collectionLocalName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final URI collectionUri = resolveIdentifier(collectionNamespace, collectionLocalName);
        collectionService.remove(collectionUri, vocabularyUri);
        LOG.debug("Collection {} removed from vocabulary {}.", collectionUri, vocabularyUri);
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description =
                    "Gets members of the unordered collection with the specified local name from the vocabulary with the specified identifier. "
                            + "Members are ordered by their labels in the vocabulary primary language.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of collection members."),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(
            value = "/{collectionLocalName}/members",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<HasIdentifier>> getMembers(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace,
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE_SIZE, required = false)
                    Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE, required = false)
                    Integer pageNo) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final URI collectionUri = resolveIdentifier(collectionNamespace, collectionLocalName);
        final Page<HasIdentifier> result = collectionService.findMembersFromUnordered(
                collectionUri, vocabularyUri, createPageRequest(pageSize, pageNo));
        return ResponseEntity.ok()
                .header(Constants.X_TOTAL_COUNT_HEADER, Long.toString(result.getTotalElements()))
                .body(result.getContent());
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description =
                    "Gets members of the ordered collection with the specified local name from the vocabulary with the specified identifier. "
                            + "Members are ordered based on the collection order.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of collection members in collection order."),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION)
    })
    @GetMapping(
            value = "/ordered/{collectionLocalName}/members",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<List<HasIdentifier>> getOrderedMembers(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace,
            @Parameter(description = ApiDocConstants.PAGE_SIZE_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE_SIZE, required = false)
                    Integer pageSize,
            @Parameter(description = ApiDocConstants.PAGE_NO_DESCRIPTION)
                    @RequestParam(name = QueryParams.PAGE, required = false)
                    Integer pageNo) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final URI collectionUri = resolveIdentifier(collectionNamespace, collectionLocalName);
        final Page<HasIdentifier> result = collectionService.findMembersFromOrdered(
                collectionUri, vocabularyUri, createPageRequest(pageSize, pageNo));
        return ResponseEntity.ok()
                .header(Constants.X_TOTAL_COUNT_HEADER, Long.toString(result.getTotalElements()))
                .body(result.getContent());
    }

    @Operation(
            security = {@SecurityRequirement(name = "bearer-key")},
            description = "Updates the position of the member in the ordered collection")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The member position was updated"),
        @ApiResponse(responseCode = "404", description = ApiDoc.ID_NOT_FOUND_DESCRIPTION),
        @ApiResponse(
                responseCode = "409",
                description = "When the specified positions or the member identifier are invalid.")
    })
    @PostMapping(
            value = "/ordered/{collectionLocalName}/members/positions",
            produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateOrderedMemberPosition(
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_LOCAL_NAME_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String localName,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_LOCAL_NAME_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_LOCAL_NAME_EXAMPLE)
                    @PathVariable
                    String collectionLocalName,
            @Parameter(
                            description = VocabularyController.ApiDoc.ID_NAMESPACE_DESCRIPTION,
                            example = VocabularyController.ApiDoc.ID_NAMESPACE_EXAMPLE)
                    @RequestParam(name = QueryParams.NAMESPACE, required = false)
                    Optional<String> namespace,
            @Parameter(
                            description = ApiDoc.ID_COLLECTION_NAMESPACE_DESCRIPTION,
                            example = ApiDoc.ID_COLLECTION_NAMESPACE_EXAMPLE)
                    @RequestParam(name = COLLECTION_NAMESPACE_PARAM)
                    String collectionNamespace,
            @Parameter(description = "The identifier of the member in the collection") @RequestParam(name = "memberIri")
                    URI memberIri,
            @Parameter(description = "The original position of the member in the collection")
                    @RequestParam(name = "originalPos")
                    int originalPos,
            @Parameter(description = "The new position of the member in the collection") @RequestParam(name = "newPos")
                    int newPos) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        final URI collectionUri = resolveIdentifier(collectionNamespace, collectionLocalName);
        collectionService.updateOrderedMemberPosition(collectionUri, vocabularyUri, memberIri, originalPos, newPos);
    }

    private URI getVocabularyUri(Optional<String> namespace, String fragment) {
        return resolveIdentifier(namespace.orElse(config.getNamespace().getVocabulary()), fragment);
    }

    private ResponseEntity<Void> persistCollection(
            String localName, Optional<String> namespace, SkosCollection<?> collection) {
        final URI vocabularyUri = getVocabularyUri(namespace, localName);
        ensureCollectionInVocabulary(collection, vocabularyUri);
        collectionService.persist(collection);
        LOG.debug("Collection {} created in vocabulary {}.", collection, vocabularyUri);
        return ResponseEntity.created(generateCollectionLocation(vocabularyUri, collection.getUri()))
                .build();
    }

    /** Generates location of the specified collection. */
    private static URI generateCollectionLocation(URI vocabularyUri, URI collectionUri) {
        final String vocabularyNamespace = IdentifierResolver.extractIdentifierNamespace(vocabularyUri);
        final String vocabularyLocalName = IdentifierResolver.extractIdentifierFragment(vocabularyUri);

        final String collectionNamespace = IdentifierResolver.extractIdentifierNamespace(collectionUri);
        final String collectionLocalName = IdentifierResolver.extractIdentifierFragment(collectionUri);

        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .queryParam(QueryParams.NAMESPACE, vocabularyNamespace)
                .queryParam(COLLECTION_NAMESPACE_PARAM, collectionNamespace)
                .path(PATH + "/{collectionLocalName}")
                .buildAndExpand(vocabularyLocalName, collectionLocalName)
                .toUri();
    }

    /**
     * Ensures that the specified collection belongs to the vocabulary resolved from the request URL.
     *
     * @param collection Collection
     * @param vocabularyUri Vocabulary identifier resolved from request
     */
    private static void ensureCollectionInVocabulary(SkosCollection<?> collection, URI vocabularyUri) {
        assertVocabulariesEqual(collection.getVocabulary(), vocabularyUri);
    }

    private static void ensureCollectionInVocabulary(SkosCollectionDto collection, URI vocabularyUri) {
        assertVocabulariesEqual(collection.getVocabulary(), vocabularyUri);
    }

    private static void assertVocabulariesEqual(URI vocabularyUri, URI vocabularyUri2) {
        Objects.requireNonNull(vocabularyUri);
        if (!Objects.equals(vocabularyUri, vocabularyUri2)) {
            throw new ValidationException("The vocabulary " + vocabularyUri
                    + ", resolved from request URL, does not match the vocabulary of the specified collection.");
        }
    }

    /** Constants for the API documentation. */
    public static final class ApiDoc {
        public static final String ID_COLLECTION_LOCAL_NAME_DESCRIPTION =
                "Locally (in the context of the collection namespace) unique part of the collection identifier.";
        public static final String ID_COLLECTION_LOCAL_NAME_EXAMPLE = "collection";
        public static final String ID_COLLECTION_NAMESPACE_DESCRIPTION =
                "Namespace against which the collection local name is resolved to obtain the collection identifier.";
        public static final String ID_COLLECTION_NAMESPACE_EXAMPLE =
                "http://onto.fel.cvut.cz/ontologies/slovnik/datovy-mpp-3.4/collection/";
        public static final String ID_NOT_FOUND_DESCRIPTION = "Vocabulary or collection not found.";

        private ApiDoc() {
            throw new AssertionError();
        }
    }
}

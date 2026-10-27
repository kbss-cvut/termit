package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SkosCollectionService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE;
import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SkosCollectionControllerTest extends BaseControllerTestRunner {

    private static final String VOCABULARY_NAME = "metropolitan-plan";
    private static final String COLLECTION_NAME = "localities";
    private static final String PATH = SkosCollectionController.PATH;
    private static final String VOCABULARY_NAMESPACE = Environment.BASE_URI + "/";
    private static final URI VOCABULARY_URI = URI.create(VOCABULARY_NAMESPACE + VOCABULARY_NAME);
    private static final String COLLECTION_NAMESPACE = VOCABULARY_URI + "/collection/";
    private static final URI COLLECTION_URI = URI.create(COLLECTION_NAMESPACE + COLLECTION_NAME);
    private static final String MEMBER_POSITIONS_PATH = PATH + "/ordered/{collection}/members/positions";
    private static final String ADD_MEMBER_OPTIONS_PATH = PATH + "/{collection}/members/options";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private IdentifierResolver idResolverMock;

    @Mock
    private SkosCollectionService collectionServiceMock;

    @InjectMocks
    private SkosCollectionController sut;

    private Vocabulary vocabulary;

    private TermCollection collection;
    private SkosCollectionDto collectionDto;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);

        collection = generateCollection();
        collectionDto = Environment.getDtoMapper().skosCollectionToDto(collection);

        lenient().doReturn(collectionDto).when(collectionServiceMock).findRequiredDto(COLLECTION_URI, VOCABULARY_URI);

        lenient().when(config.getNamespace().getVocabulary()).thenReturn(VOCABULARY_NAMESPACE);
        lenient()
                .when(idResolverMock.resolveIdentifier(VOCABULARY_NAMESPACE, VOCABULARY_NAME))
                .thenReturn(VOCABULARY_URI);
        lenient()
                .when(idResolverMock.resolveIdentifier(COLLECTION_NAMESPACE, COLLECTION_NAME))
                .thenReturn(COLLECTION_URI);
    }

    private TermCollection generateCollection() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary);
        collection.setUri(COLLECTION_URI);
        return collection;
    }

    private OrderedTermCollection generateOrderedCollection() {
        final OrderedTermCollection collection = Generator.generateOrderedTermCollection(vocabulary);
        collection.setUri(COLLECTION_URI);
        return collection;
    }

    private static List<HasIdentifier> generateMembers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (HasIdentifier) Generator.generateTermInfoWithId())
                .toList();
    }

    @Test
    void getByIdResolvesCollectionIdentifierInCollectionNamespaceAndReturnsCollectionFromService() throws Exception {
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/{collectionName}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE))
                .andExpect(status().isOk())
                .andReturn();
        final SkosCollectionDto result = readValue(mvcResult, SkosCollectionDto.class);
        assertEquals(collection.getUri(), result.getUri());
        assertEquals(collection.getLabel(), result.getLabel());
        verify(collectionServiceMock).findRequiredDto(COLLECTION_URI, VOCABULARY_URI);
    }

    @Test
    void getByIdResolvesVocabularyIdentifierInSpecifiedVocabularyNamespace() throws Exception {
        mockMvc.perform(get(PATH + "/{collectionName}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(QueryParams.NAMESPACE, VOCABULARY_NAMESPACE)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE))
                .andExpect(status().isOk());
        verify(collectionServiceMock).findRequiredDto(COLLECTION_URI, VOCABULARY_URI);
        verify(idResolverMock).resolveIdentifier(VOCABULARY_NAMESPACE, VOCABULARY_NAME);
    }

    @Test
    void getByIdReturnsBadRequestWhenCollectionNamespaceIsMissing() throws Exception {
        mockMvc.perform(get(PATH + "/{collectionName}", VOCABULARY_NAME, COLLECTION_NAME))
                .andExpect(status().isBadRequest());
        verify(collectionServiceMock, never()).findRequiredDto(any(), any());
    }

    @Test
    void getByIdReturnsNotFoundWhenServiceThrowsNotFoundException() throws Exception {
        when(collectionServiceMock.findRequiredDto(COLLECTION_URI, VOCABULARY_URI))
                .thenThrow(NotFoundException.class);

        mockMvc.perform(get(PATH + "/{collectionName}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMembersReturnsMembersOfUnorderedCollectionWithTotalCountHeader() throws Exception {
        final PageRequest pageRequest = PageRequest.of(1, 5);
        final List<HasIdentifier> members = generateMembers(pageRequest.getPageSize());
        final int totalCount = 12;
        when(collectionServiceMock.findMembersFromUnordered(COLLECTION_URI, VOCABULARY_URI, pageRequest))
                .thenReturn(new PageImpl<>(members, pageRequest, totalCount));

        final MvcResult mvcResult = mockMvc.perform(
                        get(PATH + "/{collection}/members", VOCABULARY_NAME, COLLECTION_NAME)
                                .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                                .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                                .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk())
                .andReturn();
        final List<TermInfo> result = readValue(mvcResult, new TypeReference<>() {});
        assertEquals(members, result);
        assertEquals(Integer.toString(totalCount), mvcResult.getResponse().getHeader(Constants.X_TOTAL_COUNT_HEADER));
    }

    @Test
    void getMembersPassesPageSpecificationToService() throws Exception {
        final PageRequest pageRequest = PageRequest.of(2, 5);
        when(collectionServiceMock.findMembersFromUnordered(COLLECTION_URI, VOCABULARY_URI, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get(PATH + "/{collection}/members", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk());
        verify(collectionServiceMock).findMembersFromUnordered(COLLECTION_URI, VOCABULARY_URI, pageRequest);
    }

    @Test
    void getOrderedMembersReturnsMembersOfOrderedCollectionWithTotalCountHeader() throws Exception {
        final PageRequest pageRequest = PageRequest.of(1, 5);
        final List<HasIdentifier> members = generateMembers(pageRequest.getPageSize());
        final int totalCount = 12;
        when(collectionServiceMock.findMembersFromOrdered(COLLECTION_URI, VOCABULARY_URI, pageRequest))
                .thenReturn(new PageImpl<>(members, pageRequest, totalCount));

        final MvcResult mvcResult = mockMvc.perform(
                        get(PATH + "/ordered/{collection}/members", VOCABULARY_NAME, COLLECTION_NAME)
                                .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                                .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                                .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk())
                .andReturn();
        final List<TermInfo> result = readValue(mvcResult, new TypeReference<>() {});
        assertEquals(members, result);
        assertEquals(Integer.toString(totalCount), mvcResult.getResponse().getHeader(Constants.X_TOTAL_COUNT_HEADER));
    }

    @Test
    void getOrderedMembersPassesPageSpecificationToService() throws Exception {
        final PageRequest pageRequest = PageRequest.of(2, 5);
        when(collectionServiceMock.findMembersFromOrdered(COLLECTION_URI, VOCABULARY_URI, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get(PATH + "/ordered/{collection}/members", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk());
        verify(collectionServiceMock).findMembersFromOrdered(COLLECTION_URI, VOCABULARY_URI, pageRequest);
    }

    private List<SkosCollectionDto> generateCollectionDtos(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Generator.generateTermCollection(vocabulary))
                .map(Environment.getDtoMapper()::skosCollectionToDto)
                .toList();
    }

    @Test
    void getAllReturnsCollectionsFromVocabularyWithTotalCountHeader() throws Exception {
        final PageRequest pageRequest = PageRequest.of(1, 5);
        final List<SkosCollectionDto> collections = generateCollectionDtos(pageRequest.getPageSize());
        final int totalCount = 12;
        when(collectionServiceMock.findAll(VOCABULARY_URI, null, null, pageRequest))
                .thenReturn(new PageImpl<>(collections, pageRequest, totalCount));

        final MvcResult mvcResult = mockMvc.perform(get(PATH, VOCABULARY_NAME)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk())
                .andReturn();

        final List<SkosCollectionDto> result = readValue(mvcResult, new TypeReference<>() {});
        assertEquals(
                collections.stream().map(SkosCollectionDto::getUri).toList(),
                result.stream().map(SkosCollectionDto::getUri).toList());
        assertEquals(Integer.toString(totalCount), mvcResult.getResponse().getHeader(Constants.X_TOTAL_COUNT_HEADER));
    }

    @Test
    void getAllResolvesVocabularyIdentifierInSpecifiedVocabularyNamespace() throws Exception {
        when(collectionServiceMock.findAll(any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get(PATH, VOCABULARY_NAME).param(QueryParams.NAMESPACE, VOCABULARY_NAMESPACE))
                .andExpect(status().isOk());

        verify(idResolverMock).resolveIdentifier(VOCABULARY_NAMESPACE, VOCABULARY_NAME);
        verify(collectionServiceMock).findAll(eq(VOCABULARY_URI), any(), any(), any());
    }

    @Test
    void getAllPassesPageSpecificationToService() throws Exception {
        final PageRequest pageRequest = PageRequest.of(2, 5);
        when(collectionServiceMock.findAll(VOCABULARY_URI, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get(PATH, VOCABULARY_NAME)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk());

        verify(collectionServiceMock).findAll(VOCABULARY_URI, null, null, pageRequest);
    }

    @Test
    void getAllPassesSearchStringAndSearchLanguageToService() throws Exception {
        final String searchString = "locality";
        final String searchLanguage = "cs";
        when(collectionServiceMock.findAll(any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get(PATH, VOCABULARY_NAME)
                        .param("searchString", searchString)
                        .param("searchLanguage", searchLanguage))
                .andExpect(status().isOk());

        verify(collectionServiceMock).findAll(eq(VOCABULARY_URI), eq(searchString), eq(searchLanguage), any());
    }

    @Test
    void getAllPassesNullSearchStringAndSearchLanguageToServiceWhenTheyAreMissing() throws Exception {
        when(collectionServiceMock.findAll(any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get(PATH, VOCABULARY_NAME)).andExpect(status().isOk());

        verify(collectionServiceMock).findAll(eq(VOCABULARY_URI), isNull(), isNull(), any());
    }

    @Test
    void getAllReturnsNotFoundWhenServiceThrowsNotFoundException() throws Exception {
        when(collectionServiceMock.findAll(eq(VOCABULARY_URI), any(), any(), any()))
                .thenThrow(NotFoundException.class);

        mockMvc.perform(get(PATH, VOCABULARY_NAME)).andExpect(status().isNotFound());
    }

    private static <T extends SkosCollection<?>> void assertCollectionEquals(T expected, T actual) {
        assertEquals(expected.getUri(), actual.getUri());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getVocabulary(), actual.getVocabulary());
        assertEquals(expected.getMembers(), actual.getMembers());
    }

    private static void assertCollectionEquals(SkosCollectionDto expected, SkosCollectionDto actual) {
        assertEquals(expected.getUri(), actual.getUri());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getVocabulary(), actual.getVocabulary());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getTypes(), actual.getTypes());
    }

    @Test
    void createPassesUnorderedCollectionToService() throws Exception {
        mockMvc.perform(post(PATH, VOCABULARY_NAME)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isCreated());

        final ArgumentCaptor<SkosCollection<?>> captor = ArgumentCaptor.captor();
        verify(collectionServiceMock).persist(captor.capture());

        final TermCollection result = assertInstanceOf(TermCollection.class, captor.getValue());
        assertCollectionEquals(collection, result);
    }

    @Test
    void createOrderedPassesOrderedCollectionToService() throws Exception {
        final OrderedTermCollection orderedCollection = generateOrderedCollection();
        mockMvc.perform(post(PATH + "/ordered", VOCABULARY_NAME)
                        .content(toJsonLd(orderedCollection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isCreated());

        final ArgumentCaptor<SkosCollection<?>> captor = ArgumentCaptor.captor();
        verify(collectionServiceMock).persist(captor.capture());

        final OrderedTermCollection result = assertInstanceOf(OrderedTermCollection.class, captor.getValue());
        assertCollectionEquals(orderedCollection, result);
    }

    private static String getLocationHeader(MvcResult mvcResult) {
        return mvcResult.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    @Test
    void createReturnsLocationHeaderWithCollectionLocationCollectionNamespaceAndVocabularyNamespace() throws Exception {
        final MvcResult mvcResult = mockMvc.perform(post(PATH, VOCABULARY_NAME)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isCreated())
                .andReturn();
        verifyLocationEquals("/vocabularies/" + VOCABULARY_NAME + "/collections/" + COLLECTION_NAME, mvcResult);
        assertThat(
                getLocationHeader(mvcResult),
                containsString(SkosCollectionController.COLLECTION_NAMESPACE_PARAM + "=" + COLLECTION_NAMESPACE));
        assertThat(getLocationHeader(mvcResult), containsString(QueryParams.NAMESPACE + "=" + VOCABULARY_NAMESPACE));
    }

    @Test
    void createOrderedReturnsLocationHeaderWithCollectionLocationCollectionNamespaceAndVocabularyNamespace()
            throws Exception {
        final OrderedTermCollection orderedCollection = generateOrderedCollection();
        final MvcResult mvcResult = mockMvc.perform(post(PATH + "/ordered", VOCABULARY_NAME)
                        .content(toJsonLd(orderedCollection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isCreated())
                .andReturn();
        verifyLocationEquals("/vocabularies/" + VOCABULARY_NAME + "/collections/" + COLLECTION_NAME, mvcResult);
        assertThat(
                getLocationHeader(mvcResult),
                containsString(SkosCollectionController.COLLECTION_NAMESPACE_PARAM + "=" + COLLECTION_NAMESPACE));
        assertThat(getLocationHeader(mvcResult), containsString(QueryParams.NAMESPACE + "=" + VOCABULARY_NAMESPACE));
    }

    @Test
    void createReturnsConflictWhenCollectionVocabularyDoesNotMatchRequestPath() throws Exception {
        collection.setVocabulary(Generator.generateUri());

        final MvcResult mvcResult = mockMvc.perform(post(PATH, VOCABULARY_NAME)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isConflict())
                .andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the vocabulary of the specified collection"));
        verify(collectionServiceMock, never()).persist(any());
    }

    @Test
    void createReturnsUnsupportedMediaTypeForPlainJson() throws Exception {
        mockMvc.perform(post(PATH, VOCABULARY_NAME).content(toJson(collection)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
        verify(collectionServiceMock, never()).persist(any());
    }

    @Test
    void updatePassesUnorderedCollectionToService() throws Exception {
        mockMvc.perform(put(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isNoContent());

        final ArgumentCaptor<SkosCollectionDto> captor = ArgumentCaptor.captor();
        verify(collectionServiceMock).update(captor.capture());

        assertCollectionEquals(Environment.getDtoMapper().skosCollectionToDto(collection), captor.getValue());
    }

    @Test
    void updateOrderedPassesOrderedCollectionToService() throws Exception {
        final OrderedTermCollection orderedCollection = generateOrderedCollection();

        mockMvc.perform(put(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .content(toJsonLd(orderedCollection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isNoContent());

        final ArgumentCaptor<SkosCollectionDto> captor = ArgumentCaptor.captor();
        verify(collectionServiceMock).update(captor.capture());

        assertCollectionEquals(Environment.getDtoMapper().skosCollectionToDto(orderedCollection), captor.getValue());
    }

    @Test
    void updateReturnsConflictWhenCollectionUriDoesNotMatchRequestPath() throws Exception {
        collection.setUri(Generator.generateUri());

        final MvcResult mvcResult = mockMvc.perform(put(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isConflict())
                .andReturn();

        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the ID of the specified entity"));
        verify(collectionServiceMock, never()).update(any());
    }

    @Test
    void updateReturnsConflictWhenCollectionVocabularyDoesNotMatchRequestPath() throws Exception {
        collection.setVocabulary(Generator.generateUri());

        final MvcResult mvcResult = mockMvc.perform(put(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isConflict())
                .andReturn();

        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the vocabulary of the specified collection"));
        verify(collectionServiceMock, never()).update(any());
    }

    @Test
    void updateReturnsBadRequestWhenCollectionNamespaceIsMissing() throws Exception {
        mockMvc.perform(put(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .content(toJsonLd(collection))
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isBadRequest());
        verify(collectionServiceMock, never()).update(any());
    }

    @Test
    void updateOrderedMemberPositionPassesResolvedIdentifiersAndPositionsToService() throws Exception {
        final URI memberIri = Generator.generateUri();
        mockMvc.perform(post(MEMBER_POSITIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("memberIri", memberIri.toString())
                        .param("originalPos", "2")
                        .param("newPos", "5"))
                .andExpect(status().isNoContent());
        verify(collectionServiceMock).updateOrderedMemberPosition(COLLECTION_URI, VOCABULARY_URI, memberIri, 2, 5);
    }

    @Test
    void updateOrderedMemberPositionReturnsBadRequestWhenPositionIsMissing() throws Exception {
        mockMvc.perform(post(MEMBER_POSITIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("memberIri", Generator.generateUri().toString())
                        .param("originalPos", "0"))
                .andExpect(status().isBadRequest());
        verify(collectionServiceMock, never()).updateOrderedMemberPosition(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void updateOrderedMemberPositionReturnsConflictWhenServiceThrowsValidationException() throws Exception {
        final URI memberIri = Generator.generateUri();
        doThrow(new ValidationException("Invalid position"))
                .when(collectionServiceMock)
                .updateOrderedMemberPosition(COLLECTION_URI, VOCABULARY_URI, memberIri, -1, 1);

        mockMvc.perform(post(MEMBER_POSITIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("memberIri", memberIri.toString())
                        .param("originalPos", "-1")
                        .param("newPos", "1"))
                .andExpect(status().isConflict());
    }

    @Test
    void removePassesResolvedIdentifiersToService() throws Exception {
        mockMvc.perform(delete(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE))
                .andExpect(status().isNoContent());
        verify(collectionServiceMock).remove(COLLECTION_URI, VOCABULARY_URI);
    }

    @Test
    void removeReturnsBadRequestWhenCollectionNamespaceIsMissing() throws Exception {
        mockMvc.perform(delete(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME))
                .andExpect(status().isBadRequest());
        verify(collectionServiceMock, never()).remove(any(), any());
    }

    @Test
    void removeReturnsNotFoundWhenServiceThrowsNotFoundException() throws Exception {
        doThrow(NotFoundException.class).when(collectionServiceMock).remove(COLLECTION_URI, VOCABULARY_URI);

        mockMvc.perform(delete(PATH + "/{collection}", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAddMemberOptionsReturnsTermsAndCollectionsWithTotalCountHeader() throws Exception {
        final PageRequest pageRequest = PageRequest.of(1, 5);
        final List<HasIdentifier> options = new ArrayList<>(generateMembers(3));
        options.addAll(generateCollectionDtos(2));
        final int totalCount = 12;
        when(collectionServiceMock.findAddMemberOptions(COLLECTION_URI, VOCABULARY_URI, null, pageRequest))
                .thenReturn(new PageImpl<>(options, pageRequest, totalCount));

        final MvcResult mvcResult = mockMvc.perform(get(ADD_MEMBER_OPTIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk())
                .andReturn();

        final Map<String, ?> result = readValue(mvcResult, new TypeReference<>() {});
        final List<URI> expectedUris =
                options.stream().map(HasIdentifier::getUri).toList();
        // items are in @graph, with @id aliased to "uri" by the context
        final List<URI> returnedUris = ((List<?>) result.get(JsonLd.GRAPH))
                .stream()
                        .map(item -> URI.create(((Map<?, ?>) item).get("uri").toString()))
                        .toList();
        assertEquals(expectedUris, returnedUris);
        assertEquals(Integer.toString(totalCount), mvcResult.getResponse().getHeader(Constants.X_TOTAL_COUNT_HEADER));
    }

    @Test
    void findAddMemberOptionsPassesPageSpecificationToService() throws Exception {
        final PageRequest pageRequest = PageRequest.of(2, 5);
        when(collectionServiceMock.findAddMemberOptions(COLLECTION_URI, VOCABULARY_URI, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        mockMvc.perform(get(ADD_MEMBER_OPTIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param(PAGE, Integer.toString(pageRequest.getPageNumber()))
                        .param(PAGE_SIZE, Integer.toString(pageRequest.getPageSize())))
                .andExpect(status().isOk());

        verify(collectionServiceMock).findAddMemberOptions(COLLECTION_URI, VOCABULARY_URI, null, pageRequest);
    }

    @Test
    void findAddMemberOptionsPassesSearchStringToService() throws Exception {
        final String searchString = "locality";
        when(collectionServiceMock.findAddMemberOptions(any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get(ADD_MEMBER_OPTIONS_PATH, VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("searchString", searchString)
                        .param(PAGE, "0")
                        .param(PAGE_SIZE, "5"))
                .andExpect(status().isOk());

        verify(collectionServiceMock)
                .findAddMemberOptions(eq(COLLECTION_URI), eq(VOCABULARY_URI), eq(searchString), any());
    }

    @Test
    void addMemberPassesIdentifiersToService() throws Exception {
        final URI member = Generator.generateUri();
        mockMvc.perform(post(PATH + "/{collectionLocalName}/members", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("member", member.toString()))
                .andExpect(status().isNoContent());

        verify(collectionServiceMock).addMember(COLLECTION_URI, VOCABULARY_URI, member);
    }

    @Test
    void removeMemberPassesIdentifiersToService() throws Exception {
        final URI member = Generator.generateUri();
        mockMvc.perform(delete(PATH + "/{collectionLocalName}/members", VOCABULARY_NAME, COLLECTION_NAME)
                        .param(SkosCollectionController.COLLECTION_NAMESPACE_PARAM, COLLECTION_NAMESPACE)
                        .param("member", member.toString()))
                .andExpect(status().isNoContent());

        verify(collectionServiceMock).removeMember(COLLECTION_URI, VOCABULARY_URI, member);
    }
}

/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.ResourceService;
import cz.cvut.kbss.termit.service.document.util.TypeAwareFileSystemResource;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.util.ConfigParam.NAMESPACE_RESOURCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/resources";
    private static final String IRI_PARAM = "iri";

    private static final String RESOURCE_NAME = "test-resource";
    private static final String RESOURCE_NAMESPACE = Environment.BASE_URI + "/";
    private static final URI RESOURCE_URI = URI.create(RESOURCE_NAMESPACE + RESOURCE_NAME);
    private static final String FILE_NAME = "test.html";
    private static final String HTML_CONTENT = "<html><head><title>Test</title></head><body>test</body></html>";

    @Mock
    private ResourceService resourceServiceMock;

    @Mock
    private Configuration configMock;

    @Mock
    private IdentifierResolver identifierResolverMock;

    @InjectMocks
    private ResourceController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @AfterEach
    void tearDown() {
        Environment.resetCurrentUser();
    }

    @Test
    void getTermsForNKODReturnsTermsAssignedToResourceWithSpecifiedIri() throws Exception {
        final Resource resource = Generator.generateResourceWithId();
        when(resourceServiceMock.getRequiredReference(resource.getUri())).thenReturn(resource);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId())
                .collect(Collectors.toList());
        when(resourceServiceMock.findTags(resource)).thenReturn(terms);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/resource/terms").param(IRI_PARAM, resource.getUri().toString()))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(terms, result);
        verify(resourceServiceMock).findTags(resource);
    }

    @Test
    void getTermsReturnsTermsAssignedToResource() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setUri(RESOURCE_URI);
        resource.setLabel(RESOURCE_NAME);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.getRequiredReference(RESOURCE_URI)).thenReturn(resource);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId())
                .collect(Collectors.toList());
        when(resourceServiceMock.findTags(resource)).thenReturn(terms);
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/terms").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(terms, result);
        verify(resourceServiceMock).findTags(resource);
    }

    @Test
    void getResourceRetrievesResourceByDefaultNamespaceAndSpecifiedNormalizedName() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME))
                .thenReturn(RESOURCE_URI);
        when(resourceServiceMock.findRequired(RESOURCE_URI)).thenReturn(resource);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + RESOURCE_NAME)).andExpect(status().isOk())
                .andReturn();
        final Resource result = readValue(mvcResult, Resource.class);
        assertEquals(resource, result);
        verify(resourceServiceMock).findRequired(RESOURCE_URI);
        verify(identifierResolverMock).resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME);
    }

    @Test
    void getResourceUsesSpecifiedNamespaceForResourceRetrieval() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.findRequired(RESOURCE_URI)).thenReturn(resource);
        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + RESOURCE_NAME).param("namespace", RESOURCE_NAMESPACE))
                        .andExpect(status().isOk())
                        .andReturn();
        final Resource result = readValue(mvcResult, Resource.class);
        assertEquals(resource, result);
        verify(resourceServiceMock).findRequired(RESOURCE_URI);
        verify(identifierResolverMock).resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME);
    }

    @Test
    void createResourcePassesNewResourceToService() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        mockMvc.perform(post(PATH).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        verify(resourceServiceMock).persist(resource);
    }

    @Test
    void createResourceReturnsLocationHeaderOnSuccess() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        final MvcResult mvcResult = mockMvc
                .perform(post(PATH).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON)).andReturn();
        verifyLocationEquals(PATH + "/" + RESOURCE_NAME, mvcResult);
    }

    @Test
    void createResourceReturnsLocationHeaderWithNamespaceParameterWhenItDiffersFromDefault() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        final String namespace = "http://onto.fel.cvut.cz/ontologies/test/termit/resources/";
        resource.setUri(URI.create(namespace + RESOURCE_NAME));
        final MvcResult mvcResult = mockMvc
                .perform(post(PATH).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON)).andReturn();
        verifyLocationEquals(PATH + "/" + RESOURCE_NAME, mvcResult);
        final String location = mvcResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location, containsString(QueryParams.NAMESPACE + "=" + namespace));
    }

    @Test
    void updateResourcePassesUpdateDataToService() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME)).thenReturn(resource.getUri());
        mockMvc.perform(
                put(PATH + "/" + RESOURCE_NAME).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        verify(identifierResolverMock).resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME);
        verify(resourceServiceMock).update(resource);
    }

    @Test
    void updateResourceThrowsConflictExceptionWhenRequestUrlIdentifierDiffersFromEntityIdentifier() throws Exception {
        final Resource resource = Generator.generateResourceWithId();
        resource.setLabel(RESOURCE_NAME);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        final MvcResult mvcResult = mockMvc.perform(
                put(PATH + "/" + RESOURCE_NAME).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON)
                        .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isConflict()).andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the ID of the specified entity"));
    }

    @Test
    void setTermsPassesNewTermUrisToService() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME)).thenReturn(resource.getUri());
        when(resourceServiceMock.findRequired(resource.getUri())).thenReturn(resource);
        final List<URI> uris = IntStream.range(0, 5).mapToObj(i -> Generator.generateUri())
                .collect(Collectors.toList());
        mockMvc.perform(put(PATH + "/" + RESOURCE_NAME + "/terms").content(toJson(uris))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        verify(resourceServiceMock).findRequired(resource.getUri());
        verify(resourceServiceMock).setTags(resource, uris);
    }

    @Test
    void getAllRetrievesResourcesFromUnderlyingService() throws Exception {
        final List<Resource> resources = IntStream.range(0, 5).mapToObj(i -> Generator.generateResourceWithId())
                .collect(Collectors.toList());
        when(resourceServiceMock.findAll()).thenReturn(resources);
        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andReturn();
        final List<Resource> result = readValue(mvcResult, new TypeReference<List<Resource>>() {
        });
        verify(resourceServiceMock).findAll();
        assertEquals(resources, result);
    }

    @Test
    void removeResourceRemovesResourceViaService() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(NAMESPACE_RESOURCE, RESOURCE_NAME)).thenReturn(resource.getUri());
        when(resourceServiceMock.getRequiredReference(resource.getUri())).thenReturn(resource);
        mockMvc.perform(delete(PATH + "/" + RESOURCE_NAME)).andExpect(status().isNoContent());
        verify(resourceServiceMock).getRequiredReference(resource.getUri());
        verify(resourceServiceMock).remove(resource);
    }

    @Test
    void createResourceSupportsSubtypesOfResource() throws Exception {
        final Document doc = new Document();
        doc.setLabel(RESOURCE_NAME);
        doc.setUri(RESOURCE_URI);
        final File file = generateFile();
        doc.setFiles(Collections.singleton(file));
        mockMvc.perform(post(PATH).content(toJsonLd(doc)).contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isCreated());
        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(resourceServiceMock).persist(captor.capture());
        assertEquals(doc, captor.getValue());
        assertEquals(doc.getFiles(), captor.getValue().getFiles());
    }

    private static File generateFile() {
        final File file = new File();
        file.setLabel(FILE_NAME);
        file.setUri(URI.create(RESOURCE_NAMESPACE + FILE_NAME));
        return file;
    }

    @Test
    void getContentReturnsContentOfRequestedFile() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(ConfigParam.class), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(file))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + FILE_NAME + "/content"))
                .andExpect(status().isOk()).andReturn();
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
    }

    private static java.io.File createTemporaryHtmlFile() throws Exception {
        final java.io.File file = Files.createTempFile("document", ".html").toFile();
        file.deleteOnExit();
        Files.write(file.toPath(), HTML_CONTENT.getBytes());
        return file;
    }

    @Test
    void saveContentSavesContentViaServiceAndReturnsNoContentStatus() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(ConfigParam.class), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);

        final java.io.File attachment = createTemporaryHtmlFile();
        final MockMultipartFile upload = new MockMultipartFile("file", file.getLabel(), MediaType.TEXT_HTML_VALUE,
                Files.readAllBytes(attachment.toPath())
        );
        mockMvc.perform(multipart(PATH + "/" + FILE_NAME + "/content").file(upload)
                .with(req -> {
                    req.setMethod(HttpMethod.PUT.toString());
                    return req;
                }))
                .andExpect(status().isNoContent());
        verify(resourceServiceMock).saveContent(eq(file), any(InputStream.class));
    }

    @Test
    void runTextAnalysisInvokesTextAnalysisOnSpecifiedResource() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        mockMvc.perform(put(PATH + "/" + FILE_NAME + "/text-analysis").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isNoContent());
        verify(resourceServiceMock).runTextAnalysis(file, Collections.emptySet());
    }

    @Test
    void runTextAnalysisInvokesTextAnalysisWithSpecifiedVocabulariesAsTermSources() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final Set<String> vocabularies = IntStream.range(0, 3).mapToObj(i -> Generator.generateUri().toString())
                .collect(Collectors.toSet());
        mockMvc.perform(put(PATH + "/" + FILE_NAME + "/text-analysis").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                .param("vocabulary",
                        vocabularies.toArray(new String[0])))
                .andExpect(status().isNoContent());
        verify(resourceServiceMock)
                .runTextAnalysis(file, vocabularies.stream().map(URI::create).collect(Collectors.toSet()));
    }

    @Test
    void getResourceSupportsUriWithFileExtension() throws Exception {
        final String normLabel = "CZ-00025712-CUZK_RUIAN-CSV-ADR-OB_554782.xml";
        final String namespace = "http://atom.cuzk.cz/RUIAN-CSV-ADR-OB/datasetFeeds/";
        final URI uri = URI.create(namespace + normLabel);
        final Resource resource = Generator.generateResource();
        resource.setUri(uri);
        when(identifierResolverMock.resolveIdentifier(namespace, normLabel)).thenReturn(uri);
        when(resourceServiceMock.findRequired(uri)).thenReturn(resource);
        mockMvc.perform(get(PATH + "/" + normLabel).param(QueryParams.NAMESPACE, namespace)).andExpect(status().isOk());
        verify(resourceServiceMock).findRequired(uri);
    }

    @Test
    void getAssignmentsReturnsTermAssignmentsRelatedToResource() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setUri(RESOURCE_URI);
        resource.setLabel(RESOURCE_NAME);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.getRequiredReference(RESOURCE_URI)).thenReturn(resource);
        final List<TermAssignment> assignments = IntStream.range(0, 5).mapToObj(
                i -> {
                    final TermAssignment ta = new TermAssignment(Generator.generateUri(), new Target(resource));
                    ta.setUri(Generator.generateUri());
                    return ta;
                }).collect(Collectors.toList());
        when(resourceServiceMock.findAssignments(resource)).thenReturn(assignments);
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/assignments")
                        .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<TermAssignment> result = readValue(mvcResult, new TypeReference<List<TermAssignment>>() {
        });
        assertEquals(assignments.size(), result.size());
        for (TermAssignment ta : assignments) {
            assertTrue(result.stream().anyMatch(a -> a.getUri().equals(ta.getUri())));
        }
        verify(resourceServiceMock).findAssignments(resource);
    }

    @Test
    void getFilesLoadsFilesFromDocumentWithSpecifiedIdentifier() throws Exception {
        final Document document = new Document();
        document.setLabel(RESOURCE_NAME);
        document.setUri(RESOURCE_URI);
        final File fOne = generateFile();
        document.addFile(fOne);
        when(resourceServiceMock.getRequiredReference(document.getUri())).thenReturn(document);
        when(resourceServiceMock.getFiles(document)).thenReturn(new ArrayList<>(document.getFiles()));
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(document.getUri());

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<File> result = readValue(mvcResult, new TypeReference<List<File>>() {
        });
        assertEquals(new ArrayList<>(document.getFiles()), result);
        verify(resourceServiceMock).getFiles(document);
    }

    @Test
    void getFilesReturnsConflictWhenRequestedResourceIsNotDocument() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setUri(RESOURCE_URI);
        resource.setLabel(RESOURCE_NAME);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.getRequiredReference(RESOURCE_URI)).thenReturn(resource);
        when(resourceServiceMock.getFiles(resource)).thenThrow(UnsupportedAssetOperationException.class);
        mockMvc.perform(get(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isConflict());
    }

    @Test
    void addFileToDocumentSavesFileToDocumentWithSpecifiedIdentifier() throws Exception {
        final Document document = new Document();
        document.setLabel(RESOURCE_NAME);
        document.setUri(RESOURCE_URI);
        final File fOne = generateFile();
        when(resourceServiceMock.findRequired(document.getUri())).thenReturn(document);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(document.getUri());

        mockMvc.perform(post(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                .content(toJson(fOne))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        verify(resourceServiceMock).addFileToDocument(document, fOne);
    }

    @Test
    void addFileToDocumentReturnsLocationHeaderForNewFile() throws Exception {
        final Document document = new Document();
        document.setLabel(RESOURCE_NAME);
        document.setUri(RESOURCE_URI);
        final File fOne = generateFile();
        when(resourceServiceMock.findRequired(document.getUri())).thenReturn(document);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(document.getUri());

        final MvcResult mvcResult = mockMvc
                .perform(post(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                        .content(toJson(fOne))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(PATH + "/" + fOne.getLabel(), mvcResult);
    }

    @Test
    void addFileToDocumentReturnsConflictWhenRequestedResourceIsNotDocument() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setUri(RESOURCE_URI);
        resource.setLabel(RESOURCE_NAME);
        final File fOne = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.findRequired(RESOURCE_URI)).thenReturn(resource);
        doThrow(UnsupportedAssetOperationException.class).when(resourceServiceMock).addFileToDocument(resource, fOne);
        mockMvc.perform(post(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                .content(toJson(fOne))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void removeFileFromDocumentReturnsOkForAValidFile() throws Exception {
        final Document document = new Document();
        document.setLabel(RESOURCE_NAME);
        document.setUri(RESOURCE_URI);
        final File file = generateFile();
        document.addFile(file);
        when(identifierResolverMock.resolveIdentifier(NAMESPACE_RESOURCE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        mockMvc
                .perform(delete(PATH + "/" + RESOURCE_NAME + "/files/" + FILE_NAME, RESOURCE_NAMESPACE))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeFileFromDocumentReturnsConflictWhenRequestedResourceDoesNotExist() throws Exception {
        doThrow(NotFoundException.class).when(resourceServiceMock).removeFile(any());
        mockMvc
                .perform(delete(PATH + "/" + RESOURCE_NAME + "/files/" + FILE_NAME, RESOURCE_NAMESPACE))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestTextAnalysisRecordRetrievesAnalysisRecordFromService() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final TextAnalysisRecord record = new TextAnalysisRecord(new Date((System.currentTimeMillis() / 1000) * 1000),
                file);
        record.setVocabularies(Collections.singleton(Generator.generateUri()));
        when(resourceServiceMock.findLatestTextAnalysisRecord(file)).thenReturn(record);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/text-analysis/records/latest")
                .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)).andExpect(status().isOk()).andReturn();
        final TextAnalysisRecord result = readValue(mvcResult, TextAnalysisRecord.class);
        assertNotNull(result);
        assertEquals(record.getAnalyzedResource().getUri(), result.getAnalyzedResource().getUri());
        assertEquals(record.getVocabularies(), result.getVocabularies());
        verify(resourceServiceMock).findLatestTextAnalysisRecord(file);
    }

    @Test
    void hasContentChecksForContentExistenceInService() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        when(resourceServiceMock.hasContent(file)).thenReturn(true);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(file))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        mockMvc.perform(head(PATH + "/" + FILE_NAME + "/content").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isNoContent());
        verify(resourceServiceMock).hasContent(file);
    }

    @Test
    void hasContentReturnsMimeType() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, FILE_NAME)).thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        when(resourceServiceMock.hasContent(file)).thenReturn(true);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(file))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        mockMvc.perform(head(PATH + "/" + FILE_NAME + "/content")
                .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE));

        verify(resourceServiceMock).hasContent(file);
    }

    @Test
    void getAssignmentInfoRetrievesAssignmentInfoFromService() throws Exception {
        final Resource resource = new Resource();
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.getRequiredReference(RESOURCE_URI)).thenReturn(resource);
        final List<ResourceTermAssignments> assignmentInfo = Collections.singletonList(
                new ResourceTermAssignments(Generator.generateUri(), "Test", Generator.generateUri(), RESOURCE_URI,
                        false));
        when(resourceServiceMock.getAssignmentInfo(resource)).thenReturn(assignmentInfo);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/" + RESOURCE_NAME + "/assignments/aggregated")
                        .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<ResourceTermAssignments> result = readValue(mvcResult,
                new TypeReference<List<ResourceTermAssignments>>() {
                });
        assertEquals(assignmentInfo, result);
        verify(resourceServiceMock).getAssignmentInfo(resource);
    }

    @Test
    void getContentSupportsReturningContentAsAttachment() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(ConfigParam.class), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(file))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + FILE_NAME + "/content").param("attachment", Boolean.toString(true)))
                .andExpect(status().isOk()).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                containsString("filename=\"" + FILE_NAME + "\""));
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void getAllReturnsLastModifiedHeader() throws Exception {
        final List<Resource> resources = IntStream.range(0, 5).mapToObj(i -> Generator.generateResourceWithId())
                .collect(Collectors.toList());
        when(resourceServiceMock.findAll()).thenReturn(resources);
        final long lastModified = (System.currentTimeMillis() / 1000) * 1000;
        when(resourceServiceMock.getLastModified()).thenReturn(lastModified);

        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andReturn();
        final String lastModifiedHeader = mvcResult.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModifiedHeader);
        ZonedDateTime zdt = ZonedDateTime.parse(lastModifiedHeader, DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(lastModified, zdt.toInstant().toEpochMilli());
    }

    @Test
    void getAllReturnsNotModifiedWhenLastModifiedDateIsBeforeIfModifiedSinceHeaderValue() throws Exception {
        // Round to seconds
        final long lastModified = (System.currentTimeMillis() - 60 * 1000);
        when(resourceServiceMock.getLastModified()).thenReturn(lastModified);

        mockMvc.perform(
                get(PATH).header(HttpHeaders.IF_MODIFIED_SINCE,
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())))
                .andExpect(status().isNotModified());
        verify(resourceServiceMock).getLastModified();
        verify(resourceServiceMock, never()).findAll();
    }

    @Test
    void createResourceReturnsCorrectLocationHeaderForIdentifierStartingWithConfiguredNamespace() throws Exception {
        // This applies to all asset controllers
        when(configMock.get(NAMESPACE_RESOURCE)).thenReturn(RESOURCE_NAMESPACE);
        final String newNamespace = RESOURCE_NAMESPACE + "added/";
        final Resource resource = Generator.generateResource();
        resource.setUri(URI.create(newNamespace + RESOURCE_NAME));
        final MvcResult mvcResult = mockMvc
                .perform(post(PATH).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON)).andReturn();
        verifyLocationEquals(PATH + "/" + RESOURCE_NAME, mvcResult);
        final String query = URI.create(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).getQuery();
        assertNotNull(query);
        assertEquals(QueryParams.NAMESPACE + "=" + newNamespace, query);
    }

    @Test
    void getHistoryReturnsListOfChangeRecordsForSpecifiedVocabulary() throws Exception {
        final Resource resource = Generator.generateResourceWithId();
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(resource.getUri());
        when(resourceServiceMock.getRequiredReference(RESOURCE_URI)).thenReturn(resource);
        final List<AbstractChangeRecord> records = Collections.singletonList(Generator.generatePersistChange(resource));
        when(resourceServiceMock.getChanges(resource)).thenReturn(records);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/history").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk())
                .andReturn();
        final List<AbstractChangeRecord> result = readValue(mvcResult, new TypeReference<List<AbstractChangeRecord>>() {
        });
        assertNotNull(result);
        assertEquals(records, result);
        verify(resourceServiceMock).getChanges(resource);
    }

    /**
     * Bug #1028
     */
    @Test
    void getByIdHandlesCircularReferenceBetweenDocumentAndFiles() throws Exception {
        final Document document = Generator.generateDocumentWithId();
        document.setUri(RESOURCE_URI);
        final File file = Generator.generateFileWithId("test.html");
        document.addFile(file);
        file.setDocument(document);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        when(resourceServiceMock.findRequired(RESOURCE_URI)).thenReturn(document);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/" + RESOURCE_NAME).param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                        .accept(MediaType.APPLICATION_JSON)).andReturn();
        final Document result = readValue(mvcResult, Document.class);
        assertNotNull(result);
        assertEquals(1, result.getFiles().size());
        assertEquals(file, result.getFile(file.getLabel()).get());
    }
}

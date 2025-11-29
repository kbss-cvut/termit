/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.rest.dto.FileBackupDto;
import cz.cvut.kbss.termit.rest.dto.ResourceSaveReason;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.ResourceService;
import cz.cvut.kbss.termit.service.document.ResourceRetrievalSpecification;
import cz.cvut.kbss.termit.service.document.backup.BackupFile;
import cz.cvut.kbss.termit.service.document.backup.BackupReason;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.TypeAwareFileSystemResource;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/resources";

    private static final String RESOURCE_NAME = "test-resource";
    private static final String RESOURCE_NAMESPACE = Environment.BASE_URI + "/";
    private static final URI RESOURCE_URI = URI.create(RESOURCE_NAMESPACE + RESOURCE_NAME);
    private static final String FILE_NAME = "test.html";
    private static final String HTML_CONTENT = "<html><head><title>Test</title></head><body>test</body></html>";

    @Mock
    private ResourceService resourceServiceMock;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
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
    void updateResourcePassesUpdateDataToService() throws Exception {
        final Resource resource = Generator.generateResource();
        resource.setLabel(RESOURCE_NAME);
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(configMock.getNamespace().getResource(),
                                                      RESOURCE_NAME)).thenReturn(resource.getUri());
        mockMvc.perform(
                       put(PATH + "/" + RESOURCE_NAME).content(toJson(resource)).contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        verify(identifierResolverMock).resolveIdentifier(configMock.getNamespace().getResource(), RESOURCE_NAME);
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

    private static File generateFile() {
        final File file = new File();
        file.setLabel(FILE_NAME);
        file.setUri(URI.create(RESOURCE_NAMESPACE + FILE_NAME));
        return file;
    }

    @Test
    void getContentReturnsContentOfRequestedFile() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/content"))
                .andExpect(status().isOk()).andReturn();
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
        verify(resourceServiceMock).getContent(file, new ResourceRetrievalSpecification(Optional.empty(), false));
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
        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
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
        verify(resourceServiceMock).saveContent(eq(file), any(InputStream.class), eq(ResourceSaveReason.UNKNOWN));
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
    void getFilesLoadsFilesFromDocumentWithSpecifiedIdentifier() throws Exception {
        final Document document = new Document();
        document.setLabel(RESOURCE_NAME);
        document.setUri(RESOURCE_URI);
        final File fOne = generateFile();
        document.addFile(fOne);
        when(resourceServiceMock.getReference(document.getUri())).thenReturn(document);
        when(resourceServiceMock.getFiles(document)).thenReturn(new ArrayList<>(document.getFiles()));
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(document.getUri());

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/files").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<File> result = readValue(mvcResult, new TypeReference<>() {
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
        when(resourceServiceMock.getReference(RESOURCE_URI)).thenReturn(resource);
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
        when(identifierResolverMock.resolveIdentifier(configMock.getNamespace().getResource(), FILE_NAME)).thenReturn(
                file.getUri());
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
        final TextAnalysisRecord record = new TextAnalysisRecord(Utils.timestamp(), file, Environment.LANGUAGE);
        record.setVocabularies(Collections.singleton(Generator.generateUri()));
        when(resourceServiceMock.findLatestTextAnalysisRecord(file)).thenReturn(record);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/text-analysis/records/latest")
                                                            .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                                           .andExpect(status().isOk()).andReturn();
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
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
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
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        mockMvc.perform(head(PATH + "/" + FILE_NAME + "/content")
                                .param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
               .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE));

        verify(resourceServiceMock).hasContent(file);
    }

    @Test
    void getContentSupportsReturningContentAsAttachment() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/" + FILE_NAME + "/content").param("attachment", Boolean.toString(true)))
                .andExpect(status().isOk()).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                   containsString("filename=\"" + FILE_NAME + "\""));
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void getHistoryReturnsListOfChangeRecordsForSpecifiedVocabulary() throws Exception {
        final Resource resource = Generator.generateResourceWithId();
        resource.setUri(RESOURCE_URI);
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(resource.getUri());
        when(resourceServiceMock.getReference(RESOURCE_URI)).thenReturn(resource);
        final List<AbstractChangeRecord> records = Collections.singletonList(Generator.generatePersistChange(resource));
        final ChangeRecordFilterDto emptyFilter = new ChangeRecordFilterDto();
        when(resourceServiceMock.getChanges(resource, emptyFilter)).thenReturn(records);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + RESOURCE_NAME + "/history").param(QueryParams.NAMESPACE, RESOURCE_NAMESPACE))
                .andExpect(status().isOk())
                .andReturn();
        final List<AbstractChangeRecord> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertNotNull(result);
        assertEquals(records, result);
        verify(resourceServiceMock).getChanges(resource, emptyFilter);
    }

    @Test
    void getContentWithTimestampReturnsContentOfRequestedFileAtSpecifiedTimestamp() throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        final Instant at = Utils.timestamp().truncatedTo(ChronoUnit.SECONDS);
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/content")
                                 .queryParam("at", Constants.TIMESTAMP_FORMATTER.format(at)))
                .andExpect(status().isOk()).andReturn();
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
        verify(resourceServiceMock).getContent(file, new ResourceRetrievalSpecification(Optional.of(at), false));
    }

    /**
     * Bug #258
     */
    @Test
    void updateResourceHandlesDeserializationOfDocumentFromJsonLd() throws Exception {
        final Document document = Generator.generateDocumentWithId();
        document.setUri(URI.create(RESOURCE_NAMESPACE + RESOURCE_NAME));
        when(identifierResolverMock.resolveIdentifier(RESOURCE_NAMESPACE, RESOURCE_NAME)).thenReturn(RESOURCE_URI);
        mockMvc.perform(put(PATH + "/" + RESOURCE_NAME).queryParam(QueryParams.NAMESPACE, RESOURCE_NAMESPACE)
                                                       .content(toJsonLd(document)).contentType(JsonLd.MEDIA_TYPE))
               .andExpect(status().isNoContent());
        verify(resourceServiceMock).update(document);
    }

    @Test
    void getContentWithoutUnconfirmedOccurrencesReturnsContentOfRequestedFileAtWithoutUnconfirmedTermOccurrences()
            throws Exception {
        final File file = generateFile();
        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);
        final java.io.File content = createTemporaryHtmlFile();
        when(resourceServiceMock.getContent(eq(file), any(ResourceRetrievalSpecification.class)))
                .thenReturn(new TypeAwareFileSystemResource(content, MediaType.TEXT_HTML_VALUE));
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/content")
                                 .queryParam("withoutUnconfirmedOccurrences", Boolean.toString(true)))
                .andExpect(status().isOk()).andReturn();
        final String resultContent = mvcResult.getResponse().getContentAsString();
        assertEquals(HTML_CONTENT, resultContent);
        assertEquals(MediaType.TEXT_HTML_VALUE, mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_TYPE));
        verify(resourceServiceMock).getContent(file, new ResourceRetrievalSpecification(Optional.empty(), true));
    }

    private List<BackupFile> generateEmptyBackups() {
        final int backupsCount = 7;
        Instant timestamp = Utils.timestamp();
        final List<BackupFile> backups = new ArrayList<>(backupsCount);
        for (int i = 0; i < backupsCount; i++) {
            timestamp = timestamp.plusSeconds(10);
            backups.add(new BackupFile(timestamp, null, BackupReason.UNKNOWN));
        }
        return backups;
    }

    @Test
    void getBackupCountReturnsCountOfBackupsInHeader() throws Exception {
        final File file = generateFile();
        final List<BackupFile> backups = generateEmptyBackups();
        final int backupsCount = backups.size();
        when(resourceServiceMock.getBackupFiles(eq(file))).thenReturn(backups);

        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);

        final HttpServletResponse response = mockMvc.perform(head(PATH + "/" + FILE_NAME + "/backups"))
                                                     .andExpect(status().isOk()).andReturn().getResponse();

        final String countHeader = response.getHeader(Constants.X_TOTAL_COUNT_HEADER);
        assertNotNull(countHeader);
        assertEquals(String.valueOf(backupsCount), countHeader);
    }

    @Test
    void getBackupsReturnsPagedListOfBackupFiles() throws Exception {
        final File file = generateFile();
        final List<BackupFile> backups = generateEmptyBackups();
        backups.sort(Comparator.comparing(BackupFile::timestamp).reversed());
        final int page = 5;
        final int pageSize = 1;
        final FileBackupDto expectedDto = new FileBackupDto(backups.get(page));

        when(resourceServiceMock.getBackupFiles(eq(file))).thenReturn(backups);

        when(identifierResolverMock.resolveIdentifier(any(), eq(FILE_NAME)))
                .thenReturn(file.getUri());
        when(resourceServiceMock.findRequired(file.getUri())).thenReturn(file);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FILE_NAME + "/backups")
                                                   .param("page", String.valueOf(page))
                                                   .param("pageSize", String.valueOf(pageSize)))
                                           .andExpect(status().isOk()).andReturn();

        final List<FileBackupDto> result = readValue(mvcResult, new TypeReference<>() {
        });

        assertEquals(pageSize, result.size());
        assertEquals(expectedDto,  result.get(0));
    }
}

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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.DocumentRenameEvent;
import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedTextAnalysisLanguageException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.document.ResourceRetrievalSpecification;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.transaction.TransactionSystemException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepositoryService resourceRepositoryService;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private DocumentManager documentManager;

    @Mock
    private TextAnalysisService textAnalysisService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private Configuration config = new Configuration();

    @InjectMocks
    private ResourceService sut;

    @BeforeEach
    void setUp() {
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void findRequiredLoadsResourceFromRepositoryService() {
        final URI uri = Generator.generateUri();
        sut.findRequired(uri);
        verify(resourceRepositoryService).findRequired(uri);
    }

    @Test
    void updateUpdatesResourceViaRepositoryService() {
        final Resource resource = Generator.generateResourceWithId();
        sut.update(resource);
        verify(resourceRepositoryService).update(resource);
    }

    @Test
    void removeRemovesResourceViaRepositoryService() {
        final Resource resource = Generator.generateResourceWithId();
        when(resourceRepositoryService.findRequired(resource.getUri())).thenReturn(resource);
        sut.remove(resource);
        verify(resourceRepositoryService).remove(resource);
    }

    // Bug R#1356
    @Test
    void removeEnsuresAttributesForDocumentManagerArePresent() {
        final Resource toRemove = new Resource();
        toRemove.setUri(Generator.generateUri());
        final Resource resource = Generator.generateResource();
        resource.setUri(toRemove.getUri());
        when(resourceRepositoryService.findRequired(resource.getUri())).thenReturn(resource);
        sut.remove(resource);
        verify(resourceRepositoryService).remove(resource);
        verify(documentManager).remove(resource);
    }

    @Test
    void getContentLoadsContentOfFileFromDocumentManager() {
        final File file = Generator.generateFileWithId("test.html");
        sut.getContent(file, new ResourceRetrievalSpecification(Optional.empty(), false));
        verify(documentManager).getAsResource(file);
    }

    @Test
    void getContentThrowsUnsupportedAssetOperationWhenResourceIsNotFile() {
        final Resource resource = Generator.generateResourceWithId();
        assertThrows(UnsupportedAssetOperationException.class,
                     () -> sut.getContent(resource, new ResourceRetrievalSpecification(Optional.empty(), false)));
        verify(documentManager, never()).getAsResource(any());
    }

    @Test
    void saveContentSavesFileContentViaDocumentManager() {
        final ByteArrayInputStream bis = new ByteArrayInputStream("test".getBytes());
        final File file = Generator.generateFileWithId("test.html");
        sut.saveContent(file, bis);
        verify(documentManager).saveFileContent(file, bis);
    }

    @Test
    void saveContentThrowsUnsupportedAssetOperationExceptionWhenResourceIsNotFile() {
        final ByteArrayInputStream bis = new ByteArrayInputStream("test".getBytes());
        final Resource resource = Generator.generateResourceWithId();
        assertThrows(UnsupportedAssetOperationException.class, () -> sut.saveContent(resource, bis));
        verify(documentManager, never()).saveFileContent(any(), any());
    }

    @Test
    void saveContentCreatesBackupBeforeSavingFileContentInDocumentManager() {
        final ByteArrayInputStream bis = new ByteArrayInputStream("test".getBytes());
        final File file = Generator.generateFileWithId("test.html");
        when(documentManager.exists(file)).thenReturn(true);
        sut.saveContent(file, bis);
        final InOrder inOrder = Mockito.inOrder(documentManager);
        inOrder.verify(documentManager).createBackup(file);
        inOrder.verify(documentManager).saveFileContent(file, bis);
    }

    @Test
    void saveContentDoesNotCreateBackupWhenFileDoesNotYetExist() {
        final ByteArrayInputStream bis = new ByteArrayInputStream("test".getBytes());
        final File file = Generator.generateFileWithId("test.html");
        when(documentManager.exists(file)).thenReturn(false);
        sut.saveContent(file, bis);
        verify(documentManager, never()).createBackup(file);
        verify(documentManager).saveFileContent(file, bis);
    }

    @Test
    void runTextAnalysisInvokesTextAnalysisWithVocabularyRelatedToFilesDocument() {
        final File file = Generator.generateFileWithId("test.html");
        file.setDocument(Generator.generateDocumentWithId());
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        file.getDocument().setVocabulary(vocabulary.getUri());
        when(textAnalysisService.supportsLanguage(file)).thenReturn(true);
        sut.runTextAnalysis(file, Collections.emptySet());
        verify(textAnalysisService).analyzeFile(file, Collections.singleton(vocabulary.getUri()));
    }

    @Test
    void runTextAnalysisThrowsUnsupportedAssetOperationWhenResourceIsNotFile() {
        final Resource resource = Generator.generateResourceWithId();
        assertThrows(UnsupportedAssetOperationException.class,
                     () -> sut.runTextAnalysis(resource, Collections.emptySet()));
        verify(textAnalysisService, never()).analyzeFile(any(), anySet());
    }

    @Test
    void runTextAnalysisThrowsUnsupportedAssetOperationWhenFileHasNoVocabularyAndNoVocabulariesAreSpecifiedEither() {
        final File file = Generator.generateFileWithId("test.html");
        when(textAnalysisService.supportsLanguage(file)).thenReturn(true);
        assertThrows(UnsupportedAssetOperationException.class,
                     () -> sut.runTextAnalysis(file, Collections.emptySet()));
        verify(textAnalysisService, never()).analyzeFile(any(), anySet());
    }

    @Test
    void runTextAnalysisInvokesAnalysisWithCustomVocabulariesWhenSpecified() {
        final File file = Generator.generateFileWithId("test.html");
        final Set<URI> vocabularies = new HashSet<>(Arrays.asList(Generator.generateUri(), Generator.generateUri()));
        when(textAnalysisService.supportsLanguage(file)).thenReturn(true);
        sut.runTextAnalysis(file, vocabularies);
        verify(textAnalysisService).analyzeFile(file, vocabularies);
    }

    @Test
    void runTextAnalysisInvokesAnalysisAlsoWithImportedVocabulariesOfVocabularyRElatedToFilesDocument() {
        final File file = Generator.generateFileWithId("test.html");
        file.setDocument(Generator.generateDocumentWithId());
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        file.getDocument().setVocabulary(vocabulary.getUri());
        final Set<URI> imported = new HashSet<>(Arrays.asList(Generator.generateUri(), Generator.generateUri()));
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);
        when(vocabularyService.getTransitivelyImportedVocabularies(vocabulary)).thenReturn(imported);
        when(textAnalysisService.supportsLanguage(file)).thenReturn(true);

        sut.runTextAnalysis(file, Collections.emptySet());
        final Set<URI> expected = new HashSet<>(imported);
        expected.add(vocabulary.getUri());
        verify(textAnalysisService).analyzeFile(file, expected);
        verify(vocabularyService).getTransitivelyImportedVocabularies(vocabulary);
    }

    @Test
    void runTextAnalysisInvokesAnalysisWithProvidedVocabulariesAndTheirImports() {
        final File file = Generator.generateFileWithId("test.html");
        final Vocabulary vOne = Generator.generateVocabularyWithId();
        final Set<URI> vOneImports = new HashSet<>(Arrays.asList(Generator.generateUri(), Generator.generateUri()));
        final Vocabulary vTwo = Generator.generateVocabularyWithId();
        final Set<URI> vTwoImports = Collections.singleton(Generator.generateUri());
        when(vocabularyService.getReference(vOne.getUri())).thenReturn(vOne);
        when(vocabularyService.getTransitivelyImportedVocabularies(vOne)).thenReturn(vOneImports);
        when(vocabularyService.getReference(vTwo.getUri())).thenReturn(vTwo);
        when(vocabularyService.getTransitivelyImportedVocabularies(vTwo)).thenReturn(vTwoImports);
        when(textAnalysisService.supportsLanguage(file)).thenReturn(true);

        sut.runTextAnalysis(file, new HashSet<>(Arrays.asList(vOne.getUri(), vTwo.getUri())));
        final Set<URI> expected = new HashSet<>(vOneImports);
        expected.addAll(vTwoImports);
        expected.add(vOne.getUri());
        expected.add(vTwo.getUri());
        verify(textAnalysisService).analyzeFile(file, expected);
        verify(vocabularyService).getTransitivelyImportedVocabularies(vOne);
        verify(vocabularyService).getTransitivelyImportedVocabularies(vTwo);
    }

    @Test
    void getReferenceDelegatesCallToRepositoryService() {
        final URI uri = Generator.generateUri();
        sut.getReference(uri);
        verify(resourceRepositoryService).getReference(uri);
    }

    @Test
    void getFilesReturnsFilesFromDocument() {
        final Document doc = Generator.generateDocumentWithId();
        final File fOne = Generator.generateFileWithId("test.html");
        doc.addFile(fOne);
        when(resourceRepositoryService.findRequired(doc.getUri())).thenReturn(doc);
        final List<File> result = sut.getFiles(doc);
        assertEquals(doc.getFiles().size(), result.size());
        assertTrue(doc.getFiles().containsAll(result));
        verify(resourceRepositoryService).findRequired(doc.getUri());
    }

    @Test
    void getFilesReturnsFilesSortedByLabel() {
        final Document doc = Generator.generateDocumentWithId();
        final File fOne = Generator.generateFileWithId("test.html");
        doc.addFile(fOne);
        final File fTwo = Generator.generateFileWithId("act.html");
        doc.addFile(fTwo);
        when(resourceRepositoryService.findRequired(doc.getUri())).thenReturn(doc);
        final List<File> result = sut.getFiles(doc);
        assertEquals(Arrays.asList(fTwo, fOne), result);
    }

    @Test
    void getFilesReturnsEmptyListWhenDocumentHasNoFiles() {
        final Document doc = Generator.generateDocumentWithId();
        when(resourceRepositoryService.findRequired(doc.getUri())).thenReturn(doc);
        final List<File> result = sut.getFiles(doc);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getFilesThrowsUnsupportedAssetOperationExceptionWhenSpecifiedResourceIsNotDocument() {
        final Resource resource = Generator.generateResourceWithId();
        when(resourceRepositoryService.findRequired(resource.getUri())).thenReturn(resource);
        assertThrows(UnsupportedAssetOperationException.class, () -> sut.getFiles(resource));
    }

    @Test
    void addFileToDocumentPersistsFileAndPersistsDocumentWithAddedFile() {
        final Document doc = Generator.generateDocumentWithId();
        final File fOne = Generator.generateFileWithId("test.html");
        sut.addFileToDocument(doc, fOne);
        verify(resourceRepositoryService).persist(fOne);
        verify(resourceRepositoryService).persist(doc);
    }

    @Test
    void addFileToDocumentThrowsUnsupportedAssetOperationExceptionWhenSpecifiedResourceIsNotDocument() {
        final Resource resource = Generator.generateResourceWithId();
        final File fOne = Generator.generateFileWithId("test.html");
        assertThrows(UnsupportedAssetOperationException.class, () -> sut.addFileToDocument(resource, fOne));
    }

    @Test
    void addFileToDocumentPersistsFileIntoVocabularyContextForDocumentWithVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        final File fOne = Generator.generateFileWithId("test.html");
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);

        sut.addFileToDocument(doc, fOne);
        verify(resourceRepositoryService).persist(fOne, vocabulary);
        verify(vocabularyService).getReference(vocabulary.getUri());
    }

    @Test
    void addFileToDocumentUpdatesDocumentInVocabularyContextForDocumentWithVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        final File fOne = Generator.generateFileWithId("test.html");
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);

        sut.addFileToDocument(doc, fOne);
        verify(resourceRepositoryService).persist(doc);
        verify(vocabularyService).getReference(vocabulary.getUri());
    }

    @Test
    void removeFileRemovesFileAndRemovesContent() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        final File fOne = Generator.generateFileWithId("test.html");
        fOne.setDocument(doc);

        sut.removeFile(fOne);

        verify(resourceRepositoryService).remove(fOne);
        verify(documentManager).remove(fOne);
    }

    @Test
    void removeFileThrowsTermItExceptionWhenFileIsNotLinkedToDocument() {
        final File fOne = Generator.generateFileWithId("test.html");
        assertThrows(TermItException.class, () -> sut.removeFile(fOne));
    }

    @Test
    void findLatestTextAnalysisRecordRetrievesLatestTextAnalysisRecordForResource() {
        final File file = Generator.generateFileWithId("test.html");
        final TextAnalysisRecord record = new TextAnalysisRecord(Utils.timestamp(), file, Environment.LANGUAGE);
        when(textAnalysisService.findLatestAnalysisRecord(file)).thenReturn(Optional.of(record));

        final TextAnalysisRecord result = sut.findLatestTextAnalysisRecord(file);
        assertEquals(record, result);
        verify(textAnalysisService).findLatestAnalysisRecord(file);
    }

    @Test
    void findLatestTextAnalysisRecordThrowsNotFoundExceptionWhenNoRecordExists() {
        final Resource resource = Generator.generateResourceWithId();
        when(textAnalysisService.findLatestAnalysisRecord(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> sut.findLatestTextAnalysisRecord(resource));
        verify(textAnalysisService).findLatestAnalysisRecord(resource);
    }

    @Test
    void hasContentChecksForContentExistenceInDocumentManager() {
        final File file = Generator.generateFileWithId("test.html");
        sut.hasContent(file);
        verify(documentManager).exists(file);
    }

    @Test
    void hasContentReturnsFalseForNonFile() {
        final Resource resource = Generator.generateResourceWithId();
        assertFalse(sut.hasContent(resource));
        verify(documentManager, never()).exists(any(File.class));
    }

    @Test
    void removeRemovesAssociatedDiskContent() {
        final Resource resource = Generator.generateResourceWithId();
        when(resourceRepositoryService.findRequired(resource.getUri())).thenReturn(resource);
        sut.remove(resource);
        verify(documentManager).remove(resource);
    }

    @Test
    void getLastModifiedReturnsValueFromRepositoryService() {
        final long value = System.currentTimeMillis();
        when(resourceRepositoryService.getLastModified()).thenReturn(value);
        assertEquals(value, sut.getLastModified());
        verify(resourceRepositoryService).getLastModified();
    }

    @Test
    void getChangesLoadsChangeRecordsForSpecifiedAssetFromChangeRecordService() {
        final Resource resource = Generator.generateResourceWithId();
        final List<AbstractChangeRecord> records = Collections.singletonList(Generator.generatePersistChange(resource));
        final ChangeRecordFilterDto filterDto = new ChangeRecordFilterDto();
        when(changeRecordService.getChanges(resource, filterDto)).thenReturn(records);
        assertEquals(records, sut.getChanges(resource));
        verify(changeRecordService).getChanges(resource, filterDto);
    }

    @Test
    void updatePublishesEventOnFileLabelChange() {
        final File file = Generator.generateFileWithId("newTest.html");
        final File originalFile = new File();
        originalFile.setUri(file.getUri());
        originalFile.setLabel("originalTest.html");
        when(resourceRepositoryService.findRequired(file.getUri())).thenReturn(originalFile);
        sut.update(file);
        final ArgumentCaptor<FileRenameEvent> captor = ArgumentCaptor.forClass(FileRenameEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        final FileRenameEvent event = captor.getValue();
        assertEquals(file, event.getSource());
        assertEquals(originalFile.getLabel(), event.getOriginalName());
        assertEquals(file.getLabel(), event.getNewName());
    }

    @Test
    void updatePublishesEventOnDocumentLabelChange() {
        final Document document = Generator.generateDocumentWithId();
        document.setLabel("newTest");

        final Document originalDocument = new Document();
        originalDocument.setUri(document.getUri());
        originalDocument.setLabel("originalTest");

        when(resourceRepositoryService.findRequired(document.getUri())).thenReturn(originalDocument);
        sut.update(document);
        final ArgumentCaptor<DocumentRenameEvent> captor = ArgumentCaptor.forClass(DocumentRenameEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        final DocumentRenameEvent event = captor.getValue();
        assertEquals(document, event.getSource());
        assertEquals(originalDocument.getLabel(), event.getOriginalName());
        assertEquals(document.getLabel(), event.getNewName());
    }

    @Test
    void updateDoesNotPublishFileRenameEventWhenRepositoryServiceThrowsException() {
        final File file = Generator.generateFileWithId("newTest.html");
        final File originalFile = new File();
        originalFile.setUri(file.getUri());
        originalFile.setLabel("originalTest.html");
        when(resourceRepositoryService.findRequired(file.getUri())).thenReturn(originalFile);
        when(resourceRepositoryService.update(any())).thenThrow(TransactionSystemException.class);
        assertThrows(TransactionSystemException.class, () -> sut.update(file));
        verify(eventPublisher, never()).publishEvent(any(FileRenameEvent.class));
    }

    @Test
    void removeNonEmptyDocumentRemovesAllAssociatedFiles() {
        final File file = Generator.generateFileWithId("test.html");
        final Document document = Generator.generateDocumentWithId();
        document.addFile(file);
        when(resourceRepositoryService.findRequired(document.getUri())).thenReturn(document);

        sut.remove(document);
        verify(documentManager).remove(file);
        verify(documentManager).remove(document);
        verify(resourceRepositoryService).remove(file);
        verify(resourceRepositoryService).remove(document);
    }

    @Test
    void getContentAtTimestampLoadsContentOfFileAtTimestampFromDocumentManager() {
        final File file = Generator.generateFileWithId("test.hml");
        final Instant at = Utils.timestamp();
        sut.getContent(file, new ResourceRetrievalSpecification(Optional.of(at), false));
        verify(documentManager).getAsResource(file, at);
    }

    @Test
    void getContentWithoutUnconfirmedOccurrencesRemovesUnconfirmedOccurrencesFromFileContentBeforeReturningIt()
            throws Exception {
        final File file = Generator.generateFileWithId("test.hml");
        TypeAwareResource content;
        try (final InputStream is = Environment.loadFile("data/rdfa-simple.html")) {
            content = new TypeAwareByteArrayResource(is.readAllBytes(), MediaType.TEXT_HTML_VALUE, ".html");
        }
        when(documentManager.getAsResource(file)).thenReturn(content);

        final TypeAwareResource result = sut.getContent(file,
                                                        new ResourceRetrievalSpecification(Optional.empty(), true));
        final org.jsoup.nodes.Document doc = Jsoup.parse(result.getInputStream(), StandardCharsets.UTF_8.name(), "");
        assertTrue(doc.select("span[score]").isEmpty());
    }

    @Test
    void addFileToDocumentSetsFileLanguageToDefaultConfiguredWhenNotProvided() {
        config.getPersistence().setLanguage(Environment.LANGUAGE);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document document = Generator.generateDocumentWithId();
        document.setVocabulary(vocabulary.getUri());
        final File file = Generator.generateFileWithId("test.hml");
        when(resourceRepositoryService.exists(document.getUri())).thenReturn(true);
        when(resourceRepositoryService.findRequired(document.getUri())).thenReturn(document);
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);

        sut.addFileToDocument(document, file);
        verify(resourceRepositoryService).persist(file, vocabulary);
        assertEquals(config.getPersistence().getLanguage(), file.getLanguage());
    }

    @Test
    void addFileToDocumentDoesNotModifyLanguageWhenItIsAlreadySet() {
        config.getPersistence().setLanguage(Environment.LANGUAGE);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document document = Generator.generateDocumentWithId();
        document.setVocabulary(vocabulary.getUri());
        final File file = Generator.generateFileWithId("test.hml");
        file.setLanguage("cs");
        when(resourceRepositoryService.exists(document.getUri())).thenReturn(true);
        when(resourceRepositoryService.findRequired(document.getUri())).thenReturn(document);
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);

        sut.addFileToDocument(document, file);
        verify(resourceRepositoryService).persist(file, vocabulary);
        assertEquals("cs", file.getLanguage());
    }

    @Test
    void runTextAnalysisThrowsUnsupportedTextAnalysisExceptionWhenTextAnalysisServiceDoesNotSupportFileLanguage() {
        final File file = Generator.generateFileWithId("test.html");
        file.setDocument(Generator.generateDocumentWithId());
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        file.getDocument().setVocabulary(vocabulary.getUri());
        file.setLanguage("sk");
        when(textAnalysisService.supportsLanguage(file)).thenReturn(false);
        assertThrows(UnsupportedTextAnalysisLanguageException.class, () -> sut.runTextAnalysis(file, Set.of(vocabulary.getUri())));
        verify(textAnalysisService).supportsLanguage(file);
    }
}

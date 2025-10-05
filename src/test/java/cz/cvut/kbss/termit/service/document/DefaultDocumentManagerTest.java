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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.DocumentRenameEvent;
import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.document.backup.BackupFile;
import cz.cvut.kbss.termit.service.document.backup.BackupReason;
import cz.cvut.kbss.termit.service.document.backup.DocumentBackupManager;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.Environment.loadFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDocumentManagerTest extends BaseDocumentTestRunner {
    @MockitoBean
    private DocumentBackupManager backupManager;

    private DefaultDocumentManager sut;

    @BeforeEach
    void setupSut() {
        sut = new DefaultDocumentManager(configuration, backupManager);
    }

    @Test
    void loadFileContentThrowsNotFoundExceptionIfFileCannotBeFound() throws Exception {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final File file = new File();
        file.setLabel("unknown.html");
        document.addFile(file);
        file.setDocument(document);
        assertThrows(NotFoundException.class, () -> sut.loadFileContent(file));
    }

    @Test
    void loadFileContentLoadsFileContentFromDisk() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final String result = sut.loadFileContent(file);
        assertEquals(CONTENT, result);
    }

    @Test
    void loadFileContentSupportsFileWithoutParentDocument() throws Exception {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());
        generateFileWithoutParentDocument(file);

        final String result = sut.loadFileContent(file);
        assertEquals(CONTENT, result);
    }

    private java.io.File generateFileWithoutParentDocument(File file) throws Exception {
        final java.io.File fileDir = documentDir.getParent().resolve(file.getDirectoryName()).toFile();
        fileDir.mkdir();
        fileDir.deleteOnExit();
        final java.io.File content = new java.io.File(fileDir + java.io.File.separator + file.getLabel());
        content.deleteOnExit();
        Files.write(content.toPath(), Collections.singletonList(CONTENT));
        return content;
    }

    @Test
    void getAsResourceReturnsResourceRepresentationOfFileOnDisk() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final TypeAwareResource result = sut.getAsResource(file);
        assertNotNull(result);
        assertEquals(physicalFile, result.getFile());
    }

    @Test
    void getAsResourceReturnsResourceAwareOfMediaType() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final TypeAwareResource result = sut.getAsResource(file);
        assertTrue(result.getMediaType().isPresent());
        assertEquals(MediaType.TEXT_HTML_VALUE, result.getMediaType().get());
    }

    @Test
    void saveFileContentCreatesNewFileWhenNoneExists() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        physicalFile.delete();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        sut.saveFileContent(file, content);
        assertTrue(physicalFile.exists());
    }

    @Test
    void saveFileContentOverwritesExistingFileContent() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        sut.saveFileContent(file, content);
        final java.io.File contentFile = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator +
                        document.getDirectoryName() + java.io.File.separator + file.getLabel());
        final List<String> lines = Files.readAllLines(contentFile.toPath());
        final String result = String.join("\n", lines);
        assertFalse(result.isEmpty());
    }


    @Test
    void existsReturnsTrueForExistingFile() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        assertTrue(sut.exists(file));
    }

    @Test
    void existsReturnsFalseForNonExistentFile() {
        final File file = new File();
        file.setLabel("test.html");
        document.addFile(file);
        file.setDocument(document);
        assertFalse(sut.exists(file));
    }

    @Test
    void getContentTypeResolvesHtmlContentTypeFromFileNameFromDisk() throws Exception {
        testContentType("test", ".html", CONTENT, MimeTypeUtils.TEXT_HTML_VALUE);
    }

    @Test
    void getContentTypeResolvesHtmlContentTypeFromContentFromDisk() throws Exception {
        testContentType("test", "", CONTENT, MimeTypeUtils.TEXT_HTML_VALUE);
    }

    void testContentType(final String prefix, String suffix, String content, String mimeType) throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile(prefix, suffix, content);
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final Optional<String> result = sut.getContentType(file);
        assertTrue(result.isPresent());
        assertEquals(mimeType, result.get());
    }

    @Test
    void getContentTypeThrowsNotFoundExceptionWhenFileDoesNotExist() {
        final File file = new File();
        file.setLabel("test.html");
        document.addFile(file);
        file.setDocument(document);
        assertThrows(NotFoundException.class, () -> sut.getContentType(file));
    }

    @Test
    void saveFileContentCreatesParentDirectoryWhenItDoesNotExist() throws Exception {
        final InputStream content = loadFile("data/rdfa-simple.html");
        final File file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel("test.html");
        sut.saveFileContent(file, content);
        final java.io.File physicalFile =
                documentDir.getParent().resolve(file.getDirectoryName()).resolve(file.getLabel()).toFile();
        assertTrue(physicalFile.exists());
        physicalFile.getParentFile().deleteOnExit();
        physicalFile.deleteOnExit();
    }

    @Test
    void resolveFileSanitizesFileLabelToEnsureValidFileName() throws Exception {
        final File file = new File();
        file.setUri(Generator.generateUri());
        final String label = "Zákon 130/2002";
        file.setLabel(label);
        sut.saveFileContent(file, new ByteArrayInputStream(CONTENT.getBytes()));

        String sanitizedName = IdentifierResolver.sanitizeFileName(file.getLabel());
        final java.io.File physicalFile =
                documentDir.getParent().resolve(file.getDirectoryName()).resolve(sanitizedName).toFile();
        assertTrue(physicalFile.exists());
        physicalFile.getParentFile().deleteOnExit();
        physicalFile.deleteOnExit();
    }

    @Test
    void removeRemovesPhysicalFileForFileInDocument() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        assertTrue(physicalFile.exists());
        final java.io.File docDir = physicalFile.getParentFile();
        sut.remove(file);
        assertFalse(physicalFile.exists());
        assertTrue(docDir.exists());
    }

    @Test
    void removeRemovesPhysicalFileForStandaloneFile() throws Exception {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());
        final java.io.File physicalFile = generateFileWithoutParentDocument(file);

        assertTrue(physicalFile.exists());
        sut.remove(file);
        assertFalse(physicalFile.exists());
    }

    @Test
    void removeRemovesAlsoBackupFilesOfFileInDocument() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(file);
        final java.io.File docDir = physicalFile.getParentFile();
        assertThat(docDir.list().length, greaterThan(0));
        sut.remove(file);
        assertEquals(0, docDir.list().length);
    }

    @Test
    void removeRemovesAlsoBackupFilesOfStandaloneFile() throws Exception {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());
        final java.io.File physicalFile = generateFileWithoutParentDocument(file);
        createTestBackups(file);

        final java.io.File parentDir = physicalFile.getParentFile();
        final java.io.File[] files = parentDir.listFiles();
        assertNotNull(files);
        assertThat(files.length, greaterThan(0));
        sut.remove(file);
        for (java.io.File f : files) {
            assertFalse(f.exists());
        }
    }

    @Test
    void removeRemovesParentFolderForStandaloneFile() throws Exception {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());
        final java.io.File physicalFile = generateFileWithoutParentDocument(file);
        final java.io.File parentDir = physicalFile.getParentFile();

        assertTrue(physicalFile.exists());
        sut.remove(file);
        assertFalse(physicalFile.exists());
        assertFalse(parentDir.exists());
    }

    @Test
    void removeRemovesDocumentFolderWithAllFilesItContains() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final java.io.File docDir = physicalFile.getParentFile();
        assertTrue(docDir.exists());

        sut.remove(document);
        assertFalse(physicalFile.exists());
        assertFalse(docDir.exists());
    }

    @Test
    void removeDoesNothingWhenDocumentFolderDoesNotExist() {
        sut.remove(document);
    }

    @Test
    void removeDoesNothingWhenResourceDoesNotSupportFileStorage() {
        final Resource resource = Generator.generateResourceWithId();
        sut.remove(resource);
    }

    @Test
    void removeDoesNothingWhenFileDoesNotExist() {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());

        sut.remove(file);
    }

    @Test
    void onFileRenameMovesPhysicalFileInDocumentAccordingToNewName() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        final String newName = "newFileName.html";
        file.setDocument(document);
        file.setLabel(newName);

        sut.onFileRename(new FileRenameEvent(file, physicalFile.getName(), newName));
        final java.io.File newFile = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + file.getDirectoryName() +
                        java.io.File.separator + file.getLabel());
        assertTrue(newFile.exists());
        newFile.deleteOnExit();
        assertFalse(physicalFile.exists());
    }

    @Test
    void onFileRenameDoesNothingWhenPhysicalFileDoesNotExist() {
        final File file = new File();
        file.setDocument(document);
        final String oldName = "oldFileName";
        file.setLabel("newFileName");

        sut.onFileRename(new FileRenameEvent(file, oldName, file.getLabel()));
        final java.io.File docDir = documentDir.resolve(file.getDirectoryName()).toFile();
        assertFalse(docDir.exists());
    }

    @Test
    void onFileRenameMovesBackupsOfPhysicalFileAsWell() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        final String newName = "newFileName.html";
        file.setDocument(document);
        file.setLabel(physicalFile.getName());
        final List<java.io.File> backups = createTestBackups(file);
        file.setLabel(newName);

        final java.io.File newFile = documentDir.resolve(file.getLabel()).toFile();
        newFile.deleteOnExit();

        sut.onFileRename(new FileRenameEvent(file, physicalFile.getName(), newName));
        for (java.io.File backup : backups) {
            final java.io.File newBackup = new java.io.File(documentDir +
                            java.io.File.separator + backup.getName().replace(physicalFile.getName(), newName));
            assertTrue(newBackup.exists());
            newBackup.deleteOnExit();
            assertFalse(backup.exists());
        }
        assertTrue(newFile.exists());
    }

    @Test
    void onFileRenameMovesWholeDirectoryWhenFileHasNoDocument() throws Exception {
        final File file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel("test.html");
        final java.io.File physicalOriginal = generateFileWithoutParentDocument(file);
        final String newName = "newFileName.html";
        file.setLabel(newName);

        sut.onFileRename(new FileRenameEvent(file, physicalOriginal.getName(), newName));
        final java.io.File newDirectory = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + file.getDirectoryName());
        assertTrue(newDirectory.exists());
        newDirectory.deleteOnExit();
        final java.io.File newFile = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + file.getDirectoryName() +
                        java.io.File.separator + file.getLabel());
        assertTrue(newFile.exists());
        newFile.deleteOnExit();
        assertFalse(physicalOriginal.getParentFile().exists());
        assertFalse(physicalOriginal.exists());
    }

    @Test
    void onDocumentRenameMovesWholeDirectory() throws Exception {
        final String oldDirLabel = document.getLabel();
        final java.io.File oldDirectory = documentDir.toFile();

        final String newDirLabel = "mpp";
        document.setLabel(newDirLabel);
        sut.onDocumentRename(new DocumentRenameEvent(document, oldDirLabel, document.getLabel()));

        final java.io.File newDirectory = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + document.getDirectoryName());

        assertTrue(newDirectory.exists());
        newDirectory.deleteOnExit();
        assertFalse(oldDirectory.exists());
    }

    @Test
    void getAsResourceAtCallsBackupManagerAndExtractsTheBackup() throws Exception {
        final Instant now = Utils.timestamp();
        final Instant later = now.plusSeconds(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        BackupFile backupFile = new BackupFile(now, physicalFile, BackupReason.UNKNOWN);
        when(backupManager.getBackup(any(), any())).thenReturn(backupFile);
        when(backupManager.openBackup(backupFile)).thenReturn(physicalFile);

        sut.getAsResource(file, later);
        verify(backupManager).getBackup(file, later);
        verify(backupManager).openBackup(backupFile);
    }

    @Test
    void getContentTypeResolvesMIMETypeOfSpecifiedFile() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final Optional<String> result = sut.getContentType(file);
        assertTrue(result.isPresent());
        assertEquals(MediaType.TEXT_HTML_VALUE, result.get());
    }

    @Test
    void onDocumentRenameDoesNothingWhenOldDocumentDirectoryDoesNotExist() throws Exception {
        final String oldDirLabel = document.getLabel();

        final String newDirLabel = "mpp";
        document.setLabel(newDirLabel);
        assertDoesNotThrow(
                () -> sut.onDocumentRename(new DocumentRenameEvent(document, oldDirLabel, document.getLabel())));
    }
}

/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.event.DocumentRenameEvent;
import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.Environment.loadFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(initializers = {PropertyMockingApplicationContextInitializer.class})
class DefaultDocumentManagerTest extends BaseServiceTestRunner {

    private static final String CONTENT =
            "<html><body><h1>Metropolitan plan</h1><p>Description of the metropolitan plan.</body></html>";

    @Autowired
    private Configuration configuration;

    @Autowired
    private DefaultDocumentManager sut;

    private Document document;

    @BeforeEach
    void setUp() {
        this.document = new Document();
        document.setLabel("Metropolitan plan");
        document.setUri(Generator.generateUri());
    }

    private java.io.File generateFile() throws Exception {
        return generateFile("test", ".html", CONTENT);
    }

    private java.io.File generateDirectory() throws Exception {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final java.io.File docDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
                                                             document.getDirectoryName());
        docDir.mkdir();
        docDir.deleteOnExit();
        return docDir;
    }

    private java.io.File generateFile(String filePrefix, String fileSuffix, String fileContent) throws Exception {
        final java.io.File docDir = generateDirectory();
        final java.io.File content = Files.createTempFile(docDir.toPath(), filePrefix, fileSuffix).toFile();
        content.deleteOnExit();
        Files.write(content.toPath(), Collections.singletonList(fileContent));
        return content;
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
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final java.io.File fileDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
                                                              file.getDirectoryName());
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
    void createBackupCreatesBackupFileWithIdenticalContent() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final java.io.File docDir = physicalFile.getParentFile();
        assertNotNull(docDir.listFiles());
        assertEquals(1, docDir.listFiles().length);
        sut.createBackup(file);
        assertEquals(2, docDir.listFiles().length);
        for (java.io.File f : docDir.listFiles()) {
            f.deleteOnExit();
            assertEquals(CONTENT, String.join("\n", Files.readAllLines(f.toPath())));
        }
    }

    @Test
    void createBackupCreatesBackupOfFileWithoutExtension() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        final java.io.File docDir = physicalFile.getParentFile();
        final java.io.File withoutExtension = new java.io.File(
                docDir.getAbsolutePath() + java.io.File.separator + "withoutExtension");
        withoutExtension.deleteOnExit();
        Files.copy(physicalFile.toPath(), withoutExtension.toPath());
        file.setLabel(withoutExtension.getName());
        document.addFile(file);
        file.setDocument(document);
        assertNotNull(docDir.listFiles());
        sut.createBackup(file);
        final java.io.File[] files = docDir.listFiles((d, name) -> name.startsWith("withoutExtension"));
        assertNotNull(files);
        assertEquals(2, files.length);
        for (java.io.File f : files) {
            f.deleteOnExit();
            assertEquals(CONTENT, String.join("\n", Files.readAllLines(f.toPath())));
        }
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
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final InputStream content = loadFile("data/rdfa-simple.html");
        final File file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel("test.html");
        sut.saveFileContent(file, content);
        final java.io.File physicalFile = new java.io.File(
                dir.getAbsolutePath() + java.io.File.separator + file.getDirectoryName() + java.io.File.separator +
                        file.getLabel());
        assertTrue(physicalFile.exists());
        physicalFile.getParentFile().deleteOnExit();
        physicalFile.deleteOnExit();
    }

    @Test
    void resolveFileSanitizesFileLabelToEnsureValidFileName() throws Exception {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final File file = new File();
        file.setUri(Generator.generateUri());
        final String label = "Zákon 130/2002";
        file.setLabel(label);
        sut.saveFileContent(file, new ByteArrayInputStream(CONTENT.getBytes()));

        final java.io.File physicalFile = new java.io.File(
                dir.getAbsolutePath() + java.io.File.separator + file.getDirectoryName() + java.io.File.separator +
                        IdentifierResolver.sanitizeFileName(file.getLabel()));
        assertTrue(physicalFile.exists());
        physicalFile.getParentFile().deleteOnExit();
        physicalFile.deleteOnExit();
    }

    @Test
    void createBackupSanitizesFileLabelToEnsureValidFileName() throws Exception {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        configuration.getFile().setStorage(dir.getAbsolutePath());
        final java.io.File docDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
                                                             document.getDirectoryName());
        docDir.mkdir();
        docDir.deleteOnExit();
        final File file = new File();
        file.setUri(Generator.generateUri());
        final String label = "Zákon 130/2002";
        file.setLabel(label);
        document.addFile(file);
        file.setDocument(document);
        final java.io.File content = new java.io.File(docDir, IdentifierResolver.sanitizeFileName(label));
        content.deleteOnExit();
        Files.write(content.toPath(), CONTENT.getBytes());
        sut.createBackup(file);

        final java.io.File[] files = docDir.listFiles();
        assertNotNull(files);
        assertEquals(2, files.length);
        for (java.io.File f : files) {
            try {
                assertThat(f.getName(), startsWith(IdentifierResolver.sanitizeFileName(label)));
            } finally {
                f.deleteOnExit();
            }
        }
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

        createTestBackups(physicalFile);
        final java.io.File docDir = physicalFile.getParentFile();
        assertThat(docDir.list().length, greaterThan(0));
        sut.remove(file);
        assertEquals(0, docDir.list().length);
    }

    private List<java.io.File> createTestBackups(java.io.File file) throws Exception {
        final List<java.io.File> backupFiles = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            final String path = file.getAbsolutePath();
            final String newPath = path + DefaultDocumentManager.BACKUP_NAME_SEPARATOR + DefaultDocumentManager.BACKUP_TIMESTAMP_FORMAT.format(
                    Instant.ofEpochMilli(System.currentTimeMillis() - (i + 1) * 10000));
            final java.io.File target = new java.io.File(newPath);
            Files.copy(file.toPath(), target.toPath());
            backupFiles.add(target);
            target.deleteOnExit();
        }
        // So that the oldest is first
        Collections.reverse(backupFiles);
        return backupFiles;
    }

    @Test
    void removeRemovesAlsoBackupFilesOfStandaloneFile() throws Exception {
        final File file = new File();
        file.setLabel("test-file.html");
        file.setUri(Generator.generateUri());
        final java.io.File physicalFile = generateFileWithoutParentDocument(file);
        createTestBackups(physicalFile);

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
        final java.io.File docDir = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + file.getDirectoryName());
        assertFalse(docDir.exists());
    }

    @Test
    void onFileRenameMovesBackupsOfPhysicalFileAsWell() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        final String newName = "newFileName.html";
        file.setDocument(document);
        file.setLabel(newName);
        final List<java.io.File> backups = createTestBackups(physicalFile);

        sut.onFileRename(new FileRenameEvent(file, physicalFile.getName(), newName));
        for (java.io.File backup : backups) {
            final java.io.File newBackup = new java.io.File(
                    configuration.getFile().getStorage() + java.io.File.separator +
                            file.getDirectoryName() +
                            java.io.File.separator + backup.getName().replace(physicalFile.getName(), newName));
            assertTrue(newBackup.exists());
            newBackup.deleteOnExit();
            assertFalse(backup.exists());
        }
        final java.io.File newFile = new java.io.File(
                configuration.getFile().getStorage() + java.io.File.separator + file.getDirectoryName() +
                        java.io.File.separator + file.getLabel());
        assertTrue(newFile.exists());
        newFile.deleteOnExit();
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
        final java.io.File oldDirectory = generateDirectory();

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
    void getAsResourceAtTimestampReturnsBackupCreatedMostSoonAfterTimestamp() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(physicalFile);
        final java.io.File expected = files.get(Generator.randomIndex(files));
        final String strBackupTimestamp = expected.getName().substring(
                expected.getName().indexOf(DefaultDocumentManager.BACKUP_NAME_SEPARATOR) + 1);
        final TemporalAccessor backupTimestamp = DefaultDocumentManager.BACKUP_TIMESTAMP_FORMAT.parse(
                strBackupTimestamp);
        final Instant timestamp = Instant.from(backupTimestamp).minusSeconds(5);

        final org.springframework.core.io.Resource result = sut.getAsResource(file, timestamp);
        assertEquals(expected, result.getFile());
    }

    @Test
    void getAsResourceAtTimestampReturnsOldestBackupWhenTimestampIsEpoch() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(physicalFile);
        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
        assertEquals(files.get(0), result.getFile());
    }

    @Test
    void getAsResourceWithTimestampReturnsCurrentFileWhenTimestampIsNow() throws Exception {
        // Slightly back in time to prevent issues with test speed and instant comparison
        final Instant at = Utils.timestamp().minusMillis(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(physicalFile);
        final org.springframework.core.io.Resource result = sut.getAsResource(file, at);
        assertEquals(physicalFile, result.getFile());
    }

    @Test
    void getAsResourceWithTimestampReturnsCurrentFileWhenTimestampIsInFuture() throws Exception {
        final Instant at = Utils.timestamp().plusSeconds(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(physicalFile);
        final org.springframework.core.io.Resource result = sut.getAsResource(file, at);
        assertEquals(physicalFile, result.getFile());
    }

    @Test
    void getAsResourceWithTimestampThrowsNotFoundExceptionWhenParentDocumentDirectoryDoesNotExistOnFileSystem() {
        final File file = Generator.generateFileWithId("test.html");
        document.addFile(file);
        file.setDocument(document);
        assertThrows(NotFoundException.class, () -> sut.getAsResource(file, Utils.timestamp()));
    }

    @Test
    void getAsResourceWithTimestampThrowsNotFoundExceptionWhenFileDoesNotExistInParentDocumentDirectoryOnFileSystem()
            throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        physicalFile.delete();

        assertThrows(NotFoundException.class, () -> sut.getAsResource(file, Utils.timestamp()));
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
    void getAsResourceAtTimestampHandlesLegacyBackupTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + DefaultDocumentManager.BACKUP_NAME_SEPARATOR + DateTimeFormatter.ofPattern(
                                                                                                              "yyyy-MM-dd_HHmmss_SSS")
                                                                                                      .withZone(
                                                                                                              ZoneId.systemDefault())
                                                                                                      .format(Instant.now()
                                                                                                                     .minusSeconds(
                                                                                                                             10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
        assertEquals(backup, result.getFile());
    }

    @Test
    void getAsResourceAtTimestampSkipsBackupFilesWithInvalidTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + DefaultDocumentManager.BACKUP_NAME_SEPARATOR + DateTimeFormatter.ofPattern(
                                                                                                              "yyyy-MM-dd_HHmmss")
                                                                                                      .withZone(
                                                                                                              ZoneId.systemDefault())
                                                                                                      .format(Instant.now()
                                                                                                                     .minusSeconds(
                                                                                                                             10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
        assertEquals(physicalFile, result.getFile());
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

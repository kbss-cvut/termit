package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.document.BaseDocumentTestRunner;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BackupManagerTest extends BaseDocumentTestRunner {
    private BackupManager sut;

    @BeforeEach
    void setupSut() {
        sut = new BackupManager(configuration);
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
        sut.createBackup(file, BackupReason.UNKNOWN);
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
        sut.createBackup(file, BackupReason.UNKNOWN);
        final java.io.File[] files = docDir.listFiles((d, name) -> name.startsWith("withoutExtension"));
        assertNotNull(files);
        assertEquals(2, files.length);
        for (java.io.File f : files) {
            f.deleteOnExit();
            assertEquals(CONTENT, String.join("\n", Files.readAllLines(f.toPath())));
        }
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
        sut.createBackup(file, BackupReason.UNKNOWN);

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
    void getBackupReturnsBackupCreatedMostSoonAfterTimestamp() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(file);
        final java.io.File expected = files.get(Generator.randomIndex(files));
        final String strBackupTimestamp = expected.getName().substring(
                expected.getName().indexOf(BackupFileUtils.BACKUP_NAME_SEPARATOR) + 1);
        final TemporalAccessor backupTimestamp = BackupFileUtils.BACKUP_TIMESTAMP_FORMAT.parse(
                strBackupTimestamp);
        final Instant timestamp = Instant.from(backupTimestamp).minusSeconds(5);

        final BackupFile result = sut.getBackup(file, timestamp);
        assertEquals(expected, result.file());
    }

    @Test
    void getBackupReturnsOldestBackupWhenTimestampIsEpoch()  throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(file);
        final BackupFile result = sut.getBackup(file, Instant.EPOCH);
        assertEquals(files.get(0), result.file());
    }

    @Test
    void getBackupReturnsCurrentFileWhenTimestampIsNow() throws Exception {
        // Slightly back in time to prevent issues with test speed and instant comparison
        final Instant at = Utils.timestamp().minusMillis(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(file);
        final BackupFile result = sut.getBackup(file, at);
        assertEquals(physicalFile, result.file());
    }

    @Test
    void getBackupReturnsCurrentFileWhenTimestampIsInFuture() throws Exception {
        final Instant at = Utils.timestamp().plusSeconds(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(file);
        final BackupFile result = sut.getBackup(file, at);
        assertEquals(physicalFile, result.file());
    }

    @Test
    void getBackupThrowsNotFoundExceptionWhenParentDocumentDirectoryDoesNotExistOnFileSystem() {
        final File file = Generator.generateFileWithId("test.html");
        document.addFile(file);
        file.setDocument(document);
        assertThrows(NotFoundException.class, () -> sut.getBackup(file, Utils.timestamp()));
    }

    @Test
    void getBackupThrowsNotFoundExceptionWhenFileDoesNotExistInParentDocumentDirectoryOnFileSystem()
            throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        physicalFile.delete();

        assertThrows(NotFoundException.class, () -> sut.getBackup(file, Utils.timestamp()));
    }

    @Test
    void getBackupHandlesLegacyBackupTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + BackupFileUtils.BACKUP_NAME_SEPARATOR +
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS")
                                 .withZone(ZoneId.systemDefault())
                                 .format(Instant.now().minusSeconds(10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final BackupFile result = sut.getBackup(file, Instant.EPOCH);
        assertEquals(backup, result.file());
    }

    @Test
    void getBackupSkipsBackupFilesWithInvalidTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + BackupFileUtils.BACKUP_NAME_SEPARATOR +
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
                                 .withZone(ZoneId.systemDefault())
                                 .format(Instant.now().minusSeconds(10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final BackupFile result = sut.getBackup(file, Instant.EPOCH);
        assertEquals(physicalFile, result.file());
    }
}

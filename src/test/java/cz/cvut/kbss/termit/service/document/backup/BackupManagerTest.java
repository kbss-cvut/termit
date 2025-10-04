package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

@ContextConfiguration(initializers = {PropertyMockingApplicationContextInitializer.class})
public class BackupManagerTest extends BaseServiceTestRunner {
    @Autowired
    private Configuration configuration;

    private BackupManager sut;

    private Document document;
    private Path documentDir;

    @BeforeEach
    void setUp() throws Exception {
        this.document = new Document();
        document.setLabel("Metropolitan plan");
        document.setUri(Generator.generateUri());

        Path termitStorageDir = Files.createTempDirectory("termit");
        termitStorageDir.toFile().deleteOnExit();

        documentDir = termitStorageDir.resolve(document.getDirectoryName());
        documentDir.toFile().mkdir();
        configuration.getFile().setStorage(termitStorageDir.toString());

        sut = new BackupManager(configuration);
    }

//    private java.io.File generateFile() throws Exception {
//        return generateFile("test", ".html", CONTENT);
//    }
//
//    private java.io.File generateFile(String filePrefix, String fileSuffix, String fileContent) throws Exception {
//        final java.io.File docDir = generateDirectory();
//        final java.io.File content = Files.createTempFile(docDir.toPath(), filePrefix, fileSuffix).toFile();
//        content.deleteOnExit();
//        Files.write(content.toPath(), Collections.singletonList(fileContent));
//        return content;
//    }
//
//    @Test
//    void createBackupCreatesBackupFileWithIdenticalContent() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//        final java.io.File docDir = physicalFile.getParentFile();
//        assertNotNull(docDir.listFiles());
//        assertEquals(1, docDir.listFiles().length);
//        sut.createBackup(file, BackupReason.UNKNOWN);
//        assertEquals(2, docDir.listFiles().length);
//        for (java.io.File f : docDir.listFiles()) {
//            f.deleteOnExit();
//            assertEquals(CONTENT, String.join("\n", Files.readAllLines(f.toPath())));
//        }
//    }
//
//    @Test
//    void createBackupCreatesBackupOfFileWithoutExtension() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        final java.io.File docDir = physicalFile.getParentFile();
//        final java.io.File withoutExtension = new java.io.File(
//                docDir.getAbsolutePath() + java.io.File.separator + "withoutExtension");
//        withoutExtension.deleteOnExit();
//        Files.copy(physicalFile.toPath(), withoutExtension.toPath());
//        file.setLabel(withoutExtension.getName());
//        document.addFile(file);
//        file.setDocument(document);
//        assertNotNull(docDir.listFiles());
//        sut.createBackup(file, BackupReason.UNKNOWN);
//        final java.io.File[] files = docDir.listFiles((d, name) -> name.startsWith("withoutExtension"));
//        assertNotNull(files);
//        assertEquals(2, files.length);
//        for (java.io.File f : files) {
//            f.deleteOnExit();
//            assertEquals(CONTENT, String.join("\n", Files.readAllLines(f.toPath())));
//        }
//    }
//
//
//    @Test
//    void createBackupSanitizesFileLabelToEnsureValidFileName() throws Exception {
//        final java.io.File dir = Files.createTempDirectory("termit").toFile();
//        dir.deleteOnExit();
//        configuration.getFile().setStorage(dir.getAbsolutePath());
//        final java.io.File docDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
//                document.getDirectoryName());
//        docDir.mkdir();
//        docDir.deleteOnExit();
//        final File file = new File();
//        file.setUri(Generator.generateUri());
//        final String label = "Zákon 130/2002";
//        file.setLabel(label);
//        document.addFile(file);
//        file.setDocument(document);
//        final java.io.File content = new java.io.File(docDir, IdentifierResolver.sanitizeFileName(label));
//        content.deleteOnExit();
//        Files.write(content.toPath(), CONTENT.getBytes());
//        sut.createBackup(file, BackupReason.UNKNOWN);
//
//        final java.io.File[] files = docDir.listFiles();
//        assertNotNull(files);
//        assertEquals(2, files.length);
//        for (java.io.File f : files) {
//            try {
//                assertThat(f.getName(), startsWith(IdentifierResolver.sanitizeFileName(label)));
//            } finally {
//                f.deleteOnExit();
//            }
//        }
//    }
    //
//    @Test
//    void getAsResourceAtTimestampReturnsBackupCreatedMostSoonAfterTimestamp() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        final List<java.io.File> files = createTestBackups(physicalFile);
//        final java.io.File expected = files.get(Generator.randomIndex(files));
//        final String strBackupTimestamp = expected.getName().substring(
//                expected.getName().indexOf(DefaultDocumentManager.BACKUP_NAME_SEPARATOR) + 1);
//        final TemporalAccessor backupTimestamp = DefaultDocumentManager.BACKUP_TIMESTAMP_FORMAT.parse(
//                strBackupTimestamp);
//        final Instant timestamp = Instant.from(backupTimestamp).minusSeconds(5);
//
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, timestamp);
//        assertEquals(expected, result.getFile());
//    }
//
//    @Test
//    void getAsResourceAtTimestampReturnsOldestBackupWhenTimestampIsEpoch() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        final List<java.io.File> files = createTestBackups(physicalFile);
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
//        assertEquals(files.get(0), result.getFile());
//    }

//    @Test
//    void getAsResourceWithTimestampReturnsCurrentFileWhenTimestampIsNow() throws Exception {
//        // Slightly back in time to prevent issues with test speed and instant comparison
//        final Instant at = Utils.timestamp().minusMillis(100);
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        createTestBackups(file);
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, at);
//        assertEquals(physicalFile, result.getFile());
//    }
//
//    @Test
//    void getAsResourceWithTimestampReturnsCurrentFileWhenTimestampIsInFuture() throws Exception {
//        final Instant at = Utils.timestamp().plusSeconds(100);
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        createTestBackups(file);
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, at);
//        assertEquals(physicalFile, result.getFile());
//    }
//
//    @Test
//    void getAsResourceWithTimestampThrowsNotFoundExceptionWhenParentDocumentDirectoryDoesNotExistOnFileSystem() {
//        final File file = Generator.generateFileWithId("test.html");
//        document.addFile(file);
//        file.setDocument(document);
//        assertThrows(NotFoundException.class, () -> sut.getAsResource(file, Utils.timestamp()));
//    }
//
//    @Test
//    void getAsResourceWithTimestampThrowsNotFoundExceptionWhenFileDoesNotExistInParentDocumentDirectoryOnFileSystem()
//            throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//        physicalFile.delete();
//
//        assertThrows(NotFoundException.class, () -> sut.getAsResource(file, Utils.timestamp()));
//    }



//    @Test
//    void getAsResourceAtTimestampHandlesLegacyBackupTimestampPattern() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        final String path = physicalFile.getAbsolutePath();
//        // Legacy pattern used multiple millis places
//        final String newPath = path + DefaultDocumentManager.BACKUP_NAME_SEPARATOR + DateTimeFormatter.ofPattern(
//                                                                                                              "yyyy-MM-dd_HHmmss_SSS")
//                                                                                                      .withZone(
//                                                                                                              ZoneId.systemDefault())
//                                                                                                      .format(Instant.now()
//                                                                                                                     .minusSeconds(
//                                                                                                                             10));
//        final java.io.File backup = new java.io.File(newPath);
//        Files.copy(physicalFile.toPath(), backup.toPath());
//        backup.deleteOnExit();
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
//        assertEquals(backup, result.getFile());
//    }
//
//    @Test
//    void getAsResourceAtTimestampSkipsBackupFilesWithInvalidTimestampPattern() throws Exception {
//        final File file = new File();
//        final java.io.File physicalFile = generateFile();
//        file.setLabel(physicalFile.getName());
//        document.addFile(file);
//        file.setDocument(document);
//
//        final String path = physicalFile.getAbsolutePath();
//        // Legacy pattern used multiple millis places
//        final String newPath = path + DefaultDocumentManager.BACKUP_NAME_SEPARATOR + DateTimeFormatter.ofPattern(
//                                                                                                              "yyyy-MM-dd_HHmmss")
//                                                                                                      .withZone(
//                                                                                                              ZoneId.systemDefault())
//                                                                                                      .format(Instant.now()
//                                                                                                                     .minusSeconds(
//                                                                                                                             10));
//        final java.io.File backup = new java.io.File(newPath);
//        Files.copy(physicalFile.toPath(), backup.toPath());
//        backup.deleteOnExit();
//        final org.springframework.core.io.Resource result = sut.getAsResource(file, Instant.EPOCH);
//        assertEquals(physicalFile, result.getFile());
//    }
}

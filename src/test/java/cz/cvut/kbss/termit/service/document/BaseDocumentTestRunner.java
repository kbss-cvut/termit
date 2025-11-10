package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.document.backup.BackupReason;
import cz.cvut.kbss.termit.service.document.backup.DocumentFileUtils;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ContextConfiguration(initializers = {PropertyMockingApplicationContextInitializer.class})
public abstract class BaseDocumentTestRunner extends BaseServiceTestRunner {
    protected static final String CONTENT =
            "<html><body><h1>Metropolitan plan</h1><p>Description of the metropolitan plan.</body></html>";

    @Autowired
    protected Configuration configuration;
    protected Document document;
    protected Path documentDir;

    @BeforeEach
    protected void setUp() throws Exception {
        this.document = new Document();
        document.setLabel("Metropolitan plan");
        document.setUri(Generator.generateUri());

        Path termitStorageDir = Files.createTempDirectory("termit");
        termitStorageDir.toFile().deleteOnExit();

        documentDir = termitStorageDir.resolve(document.getDirectoryName());
        documentDir.toFile().mkdir();
        configuration.getFile().setStorage(termitStorageDir.toString());
    }

    protected java.io.File generateFile() {
        try {
            return generateFile("test", ".html", CONTENT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected java.io.File generateFile(String filePrefix, String fileSuffix, String fileContent) throws Exception {
        final java.io.File content = Files.createTempFile(documentDir, filePrefix, fileSuffix).toFile();
        content.deleteOnExit();
        Files.write(content.toPath(), Collections.singletonList(fileContent));
        return content;
    }

    protected List<java.io.File> createTestBackups(File file) throws Exception {
        return createTestBackups(file, BackupReason.UNKNOWN);
    }

    protected List<java.io.File> createTestBackups(File file, BackupReason reason) throws Exception {
        final List<java.io.File> backupFiles = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Instant instant = Instant.ofEpochMilli(System.currentTimeMillis() - (i + 1) * 10000);
            final String newPath = DocumentFileUtils.generateBackupFileName(file, reason, instant) + ".bz2";
            final Path target = documentDir.resolve(newPath);
            Files.createFile(target);
            backupFiles.add(target.toFile());
            target.toFile().deleteOnExit();
        }
        // So that the oldest is first
        Collections.reverse(backupFiles);
        return backupFiles;
    }
}

package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class BackupFileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BackupFileUtils.class);
    static final String BACKUP_NAME_SEPARATOR = "~";
    static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_S")
                                                                              .withZone(ZoneId.systemDefault());
    static final int BACKUP_TIMESTAMP_LENGTH = 19;

    private BackupFileUtils() {
        throw new AssertionError();
    }
    /**
     * Resolves {@code file} inside the {@code termitDir}.
     * If instructed to verify the file existence, throws then the resolved path is not a file (or does not exist).
     * @param termitDir the directory with termit files
     * @param file the file to resol ve
     * @param verifyExists whether check for file existence
     * @return the resolved file in the file system
     */
    public static java.io.File resolveTermitFile(Path termitDir, File file, boolean verifyExists) {
        Objects.requireNonNull(termitDir);
        Objects.requireNonNull(file);
        final Path path = termitDir.resolve(file.getDirectoryName())
                                             .resolve(IdentifierResolver.sanitizeFileName(file.getLabel()));
        final java.io.File result = path.toFile();
        if (verifyExists && !result.isFile()) {
            LOG.error("File {} not found at location {}.", file, path);
            throw new NotFoundException("File " + file + " not found on file system.");
        }
        return result;
    }

    /**
     * Backup file name consists of the original file name + ~ + the current time stamp in a predefined format
     *
     * @param file File for which backup file name should be generated
     * @return Backup name
     */
    public static String generateBackupFileName(File file, BackupReason reason, Instant timestamp) {
        final String origName = IdentifierResolver.sanitizeFileName(file.getLabel());
        return origName +
                BACKUP_NAME_SEPARATOR + BACKUP_TIMESTAMP_FORMAT.format(timestamp) +
                BACKUP_NAME_SEPARATOR + reason.name();
    }
}

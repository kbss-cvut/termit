package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.exception.BackupManagerException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.FileUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2Utils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Allows to manage backups of documents in local file system
 */
@Service
public class BackupManager {
    private static final Logger LOG = LoggerFactory.getLogger(BackupManager.class);
    static final String BACKUP_NAME_SEPARATOR = "~";
    private static final int BACKUP_TIMESTAMP_LENGTH = 19;
    static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_S")
                                                                              .withZone(ZoneId.systemDefault());

    private final Path storageDirectory;

    public BackupManager(Configuration config) {
        this.storageDirectory = Path.of(config.getFile().getStorage());
    }

    /**
     * Resolves backup matching {@code at} timestamp or the first newer one.
     * @param file the backed file
     * @param at timestamp of the backup creation
     * @return backup file created at the timestamp or the oldest backup created after the timestamp.
     */
    public BackupFile getBackup(File file, Instant at) {
        final java.io.File directory = storageDirectory.resolve(file.getDirectoryName()).toFile();
        if (!directory.isDirectory()) {
            LOG.error("Document directory not found for file {} at location {}.", file, directory.getPath());
            throw new NotFoundException("File " + file + " not found on file system.");
        }

        return resolveFileVersionAt(file, at);
    }

    /**
     * Creates backup of the specified file.
     * <p>
     * Multiple backups of a file can be created and they should be distinguishable.
     *
     * @param file File to back up
     * @throws NotFoundException If the file cannot be found
     */
    public void createBackup(File file, BackupReason reason) {
        try {
            final java.io.File toBackup = FileUtils.resolveTermitFile(storageDirectory, file, true);
            final java.io.File backupFile = toBackup.toPath().getParent()
                                                     .resolve(generateBackupFileName(file, reason)).toFile();
            LOG.debug("Backing up file {} to {}.", toBackup, backupFile);
            Files.copy(toBackup.toPath(), backupFile.toPath());
            // compress the created copy
            compressFile(backupFile);
            // delete the uncompressed copy
            backupFile.delete();
        } catch (IOException e) {
            throw new BackupManagerException("Unable to backup file.", e);
        }
    }

    /**
     * Prepares a temporary file extracted from the backup
     * @param backupFile the backup file
     * @return temporary file extracted from the backup
     */
    public java.io.File openBackup(BackupFile backupFile) {
        return decompressFile(backupFile.file());
    }

    /**
     * Decompress the given BZip2 file into a newly created temporary file
     * @param file the file to decompress
     * @implSpec The decompressed file cannot be larger than 2GB
     * @see #compressFile(java.io.File) 
     */
    private java.io.File decompressFile(java.io.File file) {
        if (!file.isFile()) {
            throw new BackupManagerException("Unable to decompress non-file entry: " + file);
        }
        try {
            Path tempFile = Files.createTempFile(null, BZip2Utils.getUncompressedFileName(file.getName()));
            try (
                    // Compressed file
                    InputStream is = Files.newInputStream(file.toPath());
                    // bzip2 reads from ↑
                    CompressorInputStream cis = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, is);
                    // final decompressed file
                    OutputStream os = Files.newOutputStream(tempFile)
            ) {
                IOUtils.copy(cis, os);
            }
            return tempFile.toFile();
        } catch (IOException | CompressorException | NullPointerException e) {
            throw new BackupManagerException("Unable to decompress file.", e);
        }
    }

    /**
     * Compresses the given file with BZip2 into the same folder
     * @param file the file to compress
     * @implSpec The file cannot be larger than 2GB
     * @see #decompressFile(java.io.File)
     */
    private void compressFile(java.io.File file) {
        if (!file.isFile()) {
            throw new BackupManagerException("Unable to compress non-file entry: " + file.getAbsolutePath());
        }
        String compressedFileName = BZip2Utils.getCompressedFileName(file.getName());
        Path compressedFilePath = file.toPath().getParent().resolve(compressedFileName);
        try (
                // final bzip2 compressed file
                OutputStream fos = Files.newOutputStream(compressedFilePath);
                // bzip2 writes to ↑
                CompressorOutputStream<?> cos = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2, fos);
                InputStream is = Files.newInputStream(file.toPath())
        ) {
            IOUtils.copy(is, cos);
        } catch (IOException | CompressorException | NullPointerException e) {
            throw new BackupManagerException("Unable to compress file.", e);
        }
    }

    /**
     * Backup file name consists of the original file name + ~ + the current time stamp in a predefined format
     *
     * @param file File for which backup file name should be generated
     * @return Backup name
     */
    private String generateBackupFileName(File file, BackupReason reason) {
        final String origName = IdentifierResolver.sanitizeFileName(file.getLabel());
        return origName +
                BACKUP_NAME_SEPARATOR + BACKUP_TIMESTAMP_FORMAT.format(Utils.timestamp()) +
                BACKUP_NAME_SEPARATOR + reason.name();
    }

    /**
     * List available backups for the specified file.
     * @param file backed up file
     * @param reason reason for which backups are returned, or {@code null}
     * @return List of available backups
     */
    public List<BackupFile> getBackups(File file, BackupReason reason) {
        java.io.File directory = storageDirectory.resolve(file.getDirectoryName()).toFile();
        if (!directory.isDirectory()) {
            return List.of();
        }
        final String fileName = IdentifierResolver.sanitizeFileName(file.getLabel());
        java.io.File[] files = directory.listFiles((dir, filename) -> filename.startsWith(fileName));
        if (files == null) {
            return List.of();
        }
        Stream<BackupFile> filesStream = Arrays.stream(files).map(this::parseBackupFile).filter(Objects::nonNull);
        if (reason != null) {
            filesStream = filesStream.filter(b -> reason.equals(b.backupReason()));
        }
        return filesStream.toList();
    }

    /**
     * Parse the name of the given file in the file system resolving timestamp and backup reason.
     * <p>
     * If the timestamp is missing, the current time is used.
     * <p>
     * If the backup reason cannot be used, {@link BackupReason#UNKNOWN UNKNOWN} is used.
     * @param file the file to parse
     * @return The parsed backup file or {@code null} when parsing fails
     * when the timestamp could not be parsed.
     */
    private BackupFile parseBackupFile(java.io.File file) {
        if (!file.getName().contains(BACKUP_NAME_SEPARATOR)) {
            return new BackupFile(Utils.timestamp(), file, BackupReason.UNKNOWN);
        }
        String strTimestamp = file.getName().substring(file.getName().indexOf(BACKUP_NAME_SEPARATOR) + 1);
        // Cut off possibly legacy extra millis places
        strTimestamp = strTimestamp.substring(0, Math.min(BACKUP_TIMESTAMP_LENGTH, strTimestamp.length()));
        String strReason = file.getName().substring(file.getName().lastIndexOf(BACKUP_NAME_SEPARATOR));
        try {
            final TemporalAccessor backupTimestamp = BACKUP_TIMESTAMP_FORMAT.parse(strTimestamp);
            return new BackupFile(Instant.from(backupTimestamp), file, BackupReason.from(strReason));
        } catch (DateTimeParseException e) {
            LOG.warn("Unable to parse backup timestamp {}. Skipping file.", strTimestamp);
        }
        return null;
    }

    private BackupFile resolveFileVersionAt(File file, Instant at) {
        final List<BackupFile> backupsList = getBackups(file, null);
        final Map<Instant, BackupFile> backupMap = new HashMap<>(backupsList.size());
        final List<Instant> backupTimestamps = new ArrayList<>(backupsList.size());
        for (BackupFile backup : backupsList) {
            backupMap.put(backup.timestamp(), backup);
            backupTimestamps.add(backup.timestamp());
        }

        Collections.sort(backupTimestamps);
        for (Instant timestamp : backupTimestamps) {
            if (timestamp.isAfter(at)) {
                return backupMap.get(timestamp);
            }
        }
        LOG.warn("Unable to find version of {} valid at {}, returning current file.", file, at);
        java.io.File currentFile = FileUtils.resolveTermitFile(storageDirectory, file, true);
        return new BackupFile(Utils.timestamp(), currentFile, BackupReason.UNKNOWN);
    }
}

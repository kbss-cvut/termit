/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.event.FileRenameEvent;
import cz.cvut.kbss.termit.exception.DocumentManagerException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.document.util.TypeAwareFileSystemResource;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Default document manager uses files on filesystem to store content.
 */
@Service
public class DefaultDocumentManager implements DocumentManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDocumentManager.class);

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss_S");

    private final Configuration config;

    @Autowired
    public DefaultDocumentManager(Configuration config) {
        this.config = config;
    }

    private java.io.File resolveFile(File file, boolean verifyExists) {
        Objects.requireNonNull(file);
        final String path =
                config.get(ConfigParam.FILE_STORAGE) + java.io.File.separator + file.getDirectoryName() +
                        java.io.File.separator + IdentifierResolver.sanitizeFileName(file.getLabel());
        final java.io.File result = new java.io.File(path);
        if (verifyExists && !result.exists()) {
            LOG.error("File {} not found at location {}.", file, path);
            throw new NotFoundException("File " + file + " not found on file system.");
        }
        return result;
    }

    @Override
    public String loadFileContent(File file) {
        try {
            final java.io.File content = resolveFile(file, true);
            LOG.debug("Loading file content from {}.", content);
            final List<String> lines = Files.readAllLines(content.toPath());
            return String.join("\n", lines);
        } catch (IOException e) {
            throw new DocumentManagerException("Unable to read file.", e);
        }
    }

    @Override
    public TypeAwareResource getAsResource(File file) {
        return new TypeAwareFileSystemResource(resolveFile(file, true), getMediaType(file));
    }

    private String getMediaType(File file) {
        final java.io.File content = resolveFile(file, true);
        try {
            return Files.probeContentType(content.toPath());
        } catch (IOException e) {
            throw new DocumentManagerException("Unable to determine file content type.", e);
        }
    }

    @Override
    public void saveFileContent(File file, InputStream content) {
        try {
            final java.io.File target = resolveFile(file, false);
            LOG.debug("Saving file content to {}.", target);
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(content, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DocumentManagerException("Unable to write out file content.", e);
        }
    }

    @Override
    public void createBackup(File file) {
        try {
            final java.io.File toBackup = resolveFile(file, true);
            final java.io.File backupFile = new java.io.File(
                    toBackup.getParent() + java.io.File.separator + generateBackupFileName(file));
            LOG.debug("Backing up file {} to {}.", toBackup, backupFile);
            Files.copy(toBackup.toPath(), backupFile.toPath());
        } catch (IOException e) {
            throw new DocumentManagerException("Unable to backup file.", e);
        }
    }

    /**
     * Backup file name consists of the original file name + ~ + the current time stamp in a predefined format
     *
     * @param file File for which backup file name should be generated
     * @return Backup name
     */
    private String generateBackupFileName(File file) {
        final String origName = IdentifierResolver.sanitizeFileName(file.getLabel());
        return origName + "~" + dateFormat.format(new Date());
    }

    @Override
    public boolean exists(File file) {
        return resolveFile(file, false).exists();
    }

    @Override
    public Optional<String> getContentType(File file) {
        final java.io.File physicalFile = resolveFile(file, true);
        try {
            return Optional.ofNullable(Files.probeContentType(physicalFile.toPath()));
        } catch (IOException e) {
            LOG.error("Exception caught when determining content type of file {}.", file, e);
            return Optional.empty();
        }
    }

    @Override
    public void remove(Resource resource) {
        Objects.requireNonNull(resource);
        try {
            if (resource instanceof File) {
                removeFile((File) resource);
            } else if (resource instanceof Document) {
                removeDocumentFolderWithContent((Document) resource);
            }
        } catch (RuntimeException e) {
            throw new TermItException("Unable to remove resource document.", e);
        }
        // Do nothing for Resources not supporting content storage
    }

    private void removeFile(File file) {
        LOG.debug("Removing stored content of file {}.", file);
        final java.io.File physicalFile = resolveFile(file, false);
        if (!physicalFile.exists()) {
            return;
        }
        removeBackups(file, physicalFile);
        physicalFile.delete();
        removeParentIfNotInDocument(file, physicalFile);
    }

    private void removeBackups(File file, java.io.File physicalFile) {
        LOG.trace("Removing backups of file {}.", physicalFile);
        processBackups(file, physicalFile.getParentFile(), java.io.File::delete);
    }

    private void processBackups(File file, java.io.File directory, Consumer<java.io.File> consumer) {
        final String backupStartPattern = IdentifierResolver.sanitizeFileName(file.getLabel()) + "~";
        final java.io.File[] backups = directory.listFiles((f, fn) -> fn.startsWith(backupStartPattern));
        if (backups != null) {
            for (java.io.File backup : backups) {
                consumer.accept(backup);
            }
        }
    }

    private void removeParentIfNotInDocument(File file, java.io.File physicalFile) {
        if (file.getDocument() == null) {
            LOG.trace("Removing directory of document-less file {}.", file);
            physicalFile.getParentFile().delete();
        }
    }

    private void removeDocumentFolderWithContent(Document document) {
        LOG.debug("Removing directory of document {} together will all its content.", document);
        final String path =
                config.get(ConfigParam.FILE_STORAGE) + java.io.File.separator + document.getDirectoryName();
        final java.io.File result = new java.io.File(path);
        if (result.exists()) {
            final java.io.File[] files = result.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    f.delete();
                }
            }
            result.delete();
        }
    }

    @EventListener
    public void onFileRename(FileRenameEvent event) {
        final File tempOriginal = new File();
        tempOriginal.setUri(event.getSource().getUri());
        tempOriginal.setDocument(event.getSource().getDocument());
        tempOriginal.setLabel(event.getOriginalName());
        java.io.File physicalOriginal = resolveFile(tempOriginal, false);
        if (!physicalOriginal.exists()) {
            return;
        }
        try {
            if (tempOriginal.getDocument() == null) {
                physicalOriginal = moveFolder(event.getSource(), physicalOriginal, event);
            }
            moveFile(tempOriginal, physicalOriginal, event);
        } catch (IOException e) {
            throw new DocumentManagerException("Unable to sync file content after file renaming.", e);
        }
    }

    /**
     * If a file does not belong to any document, its content is stored in a folder whose name is derived from the file
     * name.
     * <p>
     * Therefore, if the file is renamed, this folder has to be renamed as well.
     */
    private java.io.File moveFolder(File changedFile, java.io.File physicalOriginal, FileRenameEvent event)
            throws IOException {
        final java.io.File originalDirectory = physicalOriginal.getParentFile();
        final File tmpNewFile = new File();
        tmpNewFile.setUri(changedFile.getUri());
        tmpNewFile.setLabel(event.getNewName());
        final java.io.File newDirectory = new java.io.File(originalDirectory.getParentFile().getAbsolutePath() +
                java.io.File.separator + tmpNewFile.getDirectoryName());
        LOG.trace("Moving file parent directory from '{}' to '{}' due to file rename.",
                originalDirectory.getAbsolutePath(), newDirectory.getAbsolutePath());
        Files.move(originalDirectory.toPath(), newDirectory.toPath());
        return new java.io.File(
                newDirectory.getAbsolutePath() + java.io.File.separator + physicalOriginal.getName());
    }

    private void moveFile(File original, java.io.File physicalOriginal, FileRenameEvent event) throws IOException {
        final File tempNewFile = new File();
        tempNewFile.setUri(event.getSource().getUri());
        tempNewFile.setDocument(event.getSource().getDocument());
        tempNewFile.setLabel(event.getNewName());
        final java.io.File newFile = resolveFile(tempNewFile, false);
        LOG.debug("Moving content from '{}' to '{}' due to file rename.", event.getOriginalName(),
                event.getNewName());
        Files.move(physicalOriginal.toPath(), newFile.toPath());
        moveBackupFiles(original, physicalOriginal.getParentFile(), event);
    }

    private void moveBackupFiles(File originalFile, java.io.File directory, FileRenameEvent event) {
        LOG.debug("Moving backup files.");
        processBackups(originalFile, directory, f -> {
            final String newName = f.getName().replace(event.getOriginalName(), event.getNewName());
            LOG.trace("Moving backup file from '{}' to '{}'", f.getName(), newName);
            try {
                Files.move(f.toPath(), new java.io.File(f.getParent() + java.io.File.separator + newName).toPath());
            } catch (IOException e) {
                throw new DocumentManagerException("Unable to sync backup file.", e);
            }
        });
    }
}

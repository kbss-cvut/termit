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

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.TypeAwareResource;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages the physical aspect of documents supported by the system, i.e., mainly the files stored for each document.
 * <p>
 * The object model of the system declares the notions of documents consisting of sets of files. These are all logical,
 * stored in the system's database. This class manages the physical aspect of these notions - the actual physical files
 * and their content.
 * <p>
 * Implementations may use various ways of storing the content of the files, ranging from files on file system to
 * document management systems.
 */
public interface DocumentManager {

    /**
     * Gets the content of the specified file.
     *
     * @param file File representing the physical item
     * @return Content of the file
     * @throws NotFoundException If the file cannot be found
     */
    String loadFileContent(File file);

    /**
     * Gets the file as a {@link org.springframework.core.io.Resource}.
     * <p>
     * Resource is more suitable for sending the content over network.
     *
     * @param file File representing the physical item
     * @return Resource representation of the physical item
     * @throws NotFoundException If the file cannot be found
     */
    TypeAwareResource getAsResource(File file);

    /**
     * Gets a version the file valid at the specified instant as a {@link org.springframework.core.io.Resource}.
     * <p>
     * This method can be used to retrieve a backup of the specified file. Note that if the {@code at} parameter
     * specifies a time before the file was uploaded to TermIt, the first available version is retrieved.
     *
     * @param file File representing the physical item
     * @param at   Timestamp of the version of the retrieved file
     * @return Resource representation of a matching version of the physical item
     * @throws NotFoundException If the file cannot be found
     */
    TypeAwareResource getAsResource(File file, Instant at);

    /**
     * Saves the specified content to a physical location represented by the specified file.
     * <p>
     * If no physical location exists for the specified file, a new one is created, otherwise the existing content is
     * overwritten by the new content.
     *
     * @param file    File representing the physical item
     * @param content Content to save
     */
    void saveFileContent(File file, InputStream content);

    /**
     * Creates backup of the specified file.
     * <p>
     * Multiple backups of a file can be created and they should be distinguishable.
     *
     * @param file File to backup
     * @throws NotFoundException If the file cannot be found
     */
    void createBackup(File file);

    /**
     * Checks whether content for the specified file exists.
     *
     * @param file File to check
     * @return Whether file content is stored
     */
    boolean exists(File file);

    /**
     * Determines content type of the specified file.
     *
     * @param file File whose content type is to be resolved
     * @return Resolved content type. If the system was unable to determine file content type, an empty optional is
     * returned
     * @throws NotFoundException If a corresponding physical file cannot be found
     */
    Optional<String> getContentType(File file);

    /**
     * Deletes the specified resource and all related data.
     * <p>
     * Such related data may be files contained in the document, or backups of a file.
     *
     * @param resource The resource to remove
     */
    void remove(Resource resource);
}

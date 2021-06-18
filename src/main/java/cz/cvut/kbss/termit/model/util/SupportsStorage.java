/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.model.util;

import cz.cvut.kbss.termit.util.Configuration;

/**
 * Interface implemented by assets supporting storage of data on file system.
 */
public interface SupportsStorage {

    /**
     * Gets name of the directory where files related to this instance are stored.
     * <p>
     * The name consists of normalized name of this asset, appended with hash code of this document's URI.
     * <p>
     * Note that the full directory path consists of the configured storage directory ({@link
     * Configuration.File#getStorage()}) to which the asset-specific directory name is appended.
     *
     * @return Asset-specific directory name
     */
    String getDirectoryName();
}

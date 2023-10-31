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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.util.Constants;

public enum ExportFormat {

    EXCEL(Constants.MediaType.EXCEL, ".xlsx"),
    TURTLE(Constants.MediaType.TURTLE, ".ttl"),
    RDF_XML(Constants.MediaType.RDF_XML, ".rdf");

    private final String mediaType;
    private final String fileExtension;

    ExportFormat(String mediaType, String fileExtension) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    /**
     * Resolves export format for the specified media type.
     *
     * @param mediaType Expected media type
     * @return Matching export format
     */
    public static ExportFormat ofMediaType(String mediaType) {
        for (ExportFormat f : values()) {
            if (f.getMediaType().equals(mediaType)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unsupported media type " + mediaType);
    }
}

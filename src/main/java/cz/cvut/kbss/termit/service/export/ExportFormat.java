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

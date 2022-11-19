package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.util.Constants;

public enum ExportFormat {

    CSV(Constants.MediaType.CSV, ".csv"),
    EXCEL(Constants.MediaType.EXCEL, ".xlsx"),
    TURTLE(Constants.MediaType.TURTLE, ".ttl"),
    RDF_XML(Constants.MediaType.RDF_XML, ".rdf");

    private final String mediaType;
    private final String fileExtension;

    private ExportFormat(String mediaType, String fileExtension) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}

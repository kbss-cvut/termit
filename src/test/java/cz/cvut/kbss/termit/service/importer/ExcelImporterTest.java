package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ExcelImporterTest {

    @ParameterizedTest
    @CsvSource({Constants.MediaType.EXCEL, "application/vnd.ms-excel"})
    void supportsMediaTypeReturnsTrueForSupportedExcelMediaType(String mediaType) {
        assertTrue(ExcelImporter.supportsMediaType(mediaType));
    }

    @Test
    void supportsMediaTypeReturnsFalseForUnsupportedMediaType() {
        assertFalse(ExcelImporter.supportsMediaType("application/json"));
    }
}

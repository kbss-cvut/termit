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
package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.importer.excel.ExcelImporter;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyImportersTest {

    @Mock
    private ExcelImporter excelImporter;

    @Mock
    private SKOSImporter skosImporter;

    @Mock
    private ApplicationContext appContext;

    @InjectMocks
    private VocabularyImporters sut;

    private final Vocabulary importedVocabulary = Generator.generateVocabularyWithId();

    @Test
    void importVocabularyInvokesSkosImporterForRdfSkosInput() {
        when(appContext.getBean(SKOSImporter.class)).thenReturn(skosImporter);
        when(skosImporter.importVocabulary(any(), any())).thenReturn(importedVocabulary);
        final VocabularyImporter.ImportConfiguration importConfig = new VocabularyImporter.ImportConfiguration(false,
                                                                                                               Generator.generateUri(),
                                                                                                               mock(Consumer.class));
        final VocabularyImporter.ImportInput importInput = new VocabularyImporter.ImportInput(
                Constants.MediaType.TURTLE, new ByteArrayInputStream("data".getBytes(
                StandardCharsets.UTF_8)));
        final Vocabulary result = sut.importVocabulary(importConfig, importInput);
        assertEquals(importedVocabulary, result);
        verify(skosImporter).importVocabulary(importConfig, importInput);
    }

    @Test
    void importVocabularyInvokesExcelImporterForExcelInput() {
        when(appContext.getBean(ExcelImporter.class)).thenReturn(excelImporter);
        when(excelImporter.importVocabulary(any(), any())).thenReturn(importedVocabulary);
        final VocabularyImporter.ImportConfiguration importConfig = new VocabularyImporter.ImportConfiguration(false,
                                                                                                               Generator.generateUri(),
                                                                                                               mock(Consumer.class));
        final VocabularyImporter.ImportInput importInput = new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                              new ByteArrayInputStream(
                                                                                                      "data".getBytes(
                                                                                                              StandardCharsets.UTF_8)));
        final Vocabulary result = sut.importVocabulary(importConfig, importInput);
        assertEquals(importedVocabulary, result);
        verify(excelImporter).importVocabulary(importConfig, importInput);
    }

    @Test
    void importVocabularyThrowsUnsupportedImportMediaTypeExceptionForUnsupportedMediaType() {
        final VocabularyImporter.ImportConfiguration importConfig = new VocabularyImporter.ImportConfiguration(false,
                                                                                                               Generator.generateUri(),
                                                                                                               mock(Consumer.class));
        final VocabularyImporter.ImportInput importInput = new VocabularyImporter.ImportInput("text/csv",
                                                                                              new ByteArrayInputStream(
                                                                                                      "data".getBytes(
                                                                                                              StandardCharsets.UTF_8)));

        assertThrows(UnsupportedImportMediaTypeException.class, () -> sut.importVocabulary(importConfig, importInput));
        verify(skosImporter, never()).importVocabulary(any(), any());
        verify(excelImporter, never()).importVocabulary(any(), any());
    }
}

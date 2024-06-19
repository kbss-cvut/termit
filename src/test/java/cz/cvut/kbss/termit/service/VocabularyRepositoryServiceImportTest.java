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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.importer.VocabularyImporters;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyRepositoryServiceImportTest {

    @Mock
    private VocabularyImporters importer;

    @InjectMocks
    private VocabularyRepositoryService sut;

    @Test
    void passesInputStreamFromProvidedInputFileToImporter() throws IOException {
        final MultipartFile input = new MockMultipartFile("vocabulary.ttl", "vocabulary.ttl",
                                                          Constants.MediaType.TURTLE,
                                                          Environment.loadFile("data/test-vocabulary.ttl"));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(importer.importVocabulary(any(VocabularyImporter.ImportConfiguration.class),
                                       any(VocabularyImporter.ImportInput.class))).thenReturn(vocabulary);
        final Vocabulary result = sut.importVocabulary(vocabulary.getUri(), input);
        final ArgumentCaptor<VocabularyImporter.ImportInput> captor = ArgumentCaptor.forClass(
                VocabularyImporter.ImportInput.class);
        verify(importer).importVocabulary(any(VocabularyImporter.ImportConfiguration.class), captor.capture());
        assertNotNull(captor.getValue());
        assertEquals(vocabulary, result);
    }
}

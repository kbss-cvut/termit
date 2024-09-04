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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private ResourceAuthorizationService sut;

    @Test
    void canModifyReturnsTrueForResourceNotInstanceOfDocumentOrFile() {
        assertTrue(sut.canModify(Generator.generateResourceWithId()));
    }

    @Test
    void canModifyReturnsTrueForDocumentWithoutVocabulary() {
        final Document doc = Generator.generateDocumentWithId();
        assertNull(doc.getVocabulary());
        assertTrue(sut.canModify(doc));
    }

    @Test
    void canModifyReturnsTrueForFileWithoutDocument() {
        final File file = Generator.generateFileWithId("test.html");
        assertNull(file.getDocument());
        assertTrue(sut.canModify(file));
    }

    @Test
    void canModifyChecksIfAssociatedVocabularyIsModifiableForDocument() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(false);

        assertFalse(sut.canModify(doc));
        verify(vocabularyAuthorizationService).canModify(vocabulary);
    }

    @Test
    void canModifyChecksIfAssociatedVocabularyIsModifiedForFileInVocabularyDocument() {
        final File file = Generator.generateFileWithId("test.html");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        doc.addFile(file);
        file.setDocument(doc);
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(file));
        verify(vocabularyAuthorizationService).canModify(vocabulary);
    }

    @Test
    void canRemoveChecksIfFilesCanBeRemovedFromAssociatedVocabularyForFIleInVocabularyDocument() {
        final File file = Generator.generateFileWithId("test.html");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Document doc = Generator.generateDocumentWithId();
        doc.setVocabulary(vocabulary.getUri());
        doc.addFile(file);
        file.setDocument(doc);
        when(vocabularyAuthorizationService.canRemoveFiles(vocabulary)).thenReturn(false);

        assertFalse(sut.canRemove(file));
        verify(vocabularyAuthorizationService).canRemoveFiles(vocabulary);
    }
}

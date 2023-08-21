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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.ResourceDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceRepositoryServiceTest {

    @Mock
    private ResourceDao resourceDao;

    @Mock
    private TermOccurrenceDao occurrenceDao;

    @Spy
    private IdentifierResolver idResolver = new IdentifierResolver();

    @Spy
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Spy
    private Configuration config = new Configuration();

    @InjectMocks
    private ResourceRepositoryService sut;

    @BeforeEach
    void setUp() {
        Environment.setCurrentUser(Generator.generateUserWithId());
    }

    @Test
    void persistThrowsValidationExceptionWhenResourceLabelIsMissing() {
        final Resource resource = Generator.generateResourceWithId();
        resource.setLabel(null);
        assertThrows(ValidationException.class, () -> sut.persist(resource));
        verify(resourceDao, never()).persist(any(Resource.class));
    }

    @Test
    void removeDeletesOccurrenceTargetsAndTermOccurrencesAssociatedWithResource() {
        final File file = Generator.generateFileWithId("test.txt");

        sut.remove(file);
        verify(resourceDao).remove(file);
        verify(occurrenceDao).removeAll(file);
    }

    @Test
    void persistGeneratesResourceIdentifierWhenItIsNotSet() {
        config.getNamespace().setResource(Vocabulary.s_c_zdroj + "/");
        final Resource resource = Generator.generateResource();
        assertNull(resource.getUri());
        sut.persist(resource);
        assertNotNull(resource.getUri());
        verify(idResolver).generateIdentifier(config.getNamespace().getResource(), resource.getLabel());
        verify(resourceDao).persist(resource);
    }

    @Test
    void persistThrowsResourceExistsExceptionWhenResourceIdentifierAlreadyExists() {
        final Resource existing = Generator.generateResourceWithId();
        when(resourceDao.exists(existing.getUri())).thenReturn(true);

        final Resource toPersist = Generator.generateResource();
        toPersist.setUri(existing.getUri());
        assertThrows(ResourceExistsException.class, () -> sut.persist(toPersist));
    }

    @Test
    void removeDeletesReferenceFromParentDocumentToRemovedFile() {
        final File file = Generator.generateFileWithId("test.html");
        final Document parent = Generator.generateDocumentWithId();
        parent.addFile(file);
        file.setDocument(parent);   // Manually set the inferred attribute

        sut.remove(file);

        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(resourceDao).update(captor.capture());
        assertThat(captor.getValue().getFiles(), anyOf(nullValue(), empty()));
    }
}

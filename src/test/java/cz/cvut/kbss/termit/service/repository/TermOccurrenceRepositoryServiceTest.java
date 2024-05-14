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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermOccurrenceRepositoryServiceTest {

    @Mock
    private TermOccurrenceDao dao;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private ResourceRepositoryService resourceService;

    @InjectMocks
    private TermOccurrenceRepositoryService sut;

    @Test
    void persistOccurrenceSavesSpecifiedOccurrenceIntoRepository() {
        final Term term = Generator.generateTermWithId();
        when(termService.exists(term.getUri())).thenReturn(true);
        final File resource = Generator.generateFileWithId("test.html");
        when(resourceService.exists(resource.getUri())).thenReturn(true);
        final TermDefinitionSource definitionSource = new TermDefinitionSource(term.getUri(),
                                                                               new FileOccurrenceTarget(resource));
        definitionSource.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));

        sut.persist(definitionSource);
        verify(dao).persist(definitionSource);
    }

    @Test
    void persistThrowsValidationExceptionWhenReferencedTermDoesNotExist() {
        final File resource = Generator.generateFileWithId("test.html");
        final TermOccurrence occurrence = new TermFileOccurrence(Generator.generateUri(),
                                                                 new FileOccurrenceTarget(resource));
        occurrence.getTarget().setSelectors(Set.of(new TextQuoteSelector("test text")));

        final ValidationException ex = assertThrows(ValidationException.class, () -> sut.persist(occurrence));
        assertThat(ex.getMessage(), containsString("references an unknown term"));
        assertThat(ex.getMessage(), containsString(occurrence.getTerm().toString()));
    }

    @Test
    void persistThrowsValidationExceptionWhenTargetAssetDoesNotExist() {
        final Term term = Generator.generateTermWithId();
        when(termService.exists(term.getUri())).thenReturn(true);
        final TermOccurrence occurrence = new TermFileOccurrence(term.getUri(), new FileOccurrenceTarget());
        occurrence.getTarget().setSource(Generator.generateUri());
        occurrence.getTarget().setSelectors(Set.of(new TextQuoteSelector("test text")));

        final ValidationException ex = assertThrows(ValidationException.class, () -> sut.persist(occurrence));
        assertThat(ex.getMessage(), containsString("references an unknown asset"));
        assertThat(ex.getMessage(), containsString(occurrence.getTarget().getSource().toString()));
    }
}

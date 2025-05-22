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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidLanguageConstantException;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.language.TermStateLanguageService;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageServiceTest {

    private static final List<RdfsResource> TERM_STATE_RESOURCES = Stream.of(Generator.TERM_STATES)
                                                                         .map(u -> new RdfsResource(u, MultilingualString.create("Label", Environment.LANGUAGE), null, Vocabulary.s_c_stav_pojmu))
                                                                         .collect(Collectors.toList());

    @Mock
    private DataRepositoryService dataService;

    @Mock
    private TermStateLanguageService stateLanguageService;

    @InjectMocks
    private LanguageService sut;

    @Test
    void getAccessLevelsRetrievesResourcesRepresentingEachOfAccessLevelConstants() {
        final RdfsResource res = new RdfsResource(URI.create(Vocabulary.s_i_cteni), new LangString("Read",
                Environment.LANGUAGE),
                null, null);
        when(dataService.find(any(URI.class))).thenReturn(Optional.of(res));

        final List<RdfsResource> result = sut.getAccessLevels();
        assertEquals(AccessLevel.values().length, result.size());
        Stream.of(AccessLevel.values()).forEach(al -> verify(dataService).find(URI.create(al.getIri())));
    }

    @Test
    void verifyStateExistsVerifiesExistenceOfSpecifiedState() {
        when(stateLanguageService.getTermStates()).thenReturn(TERM_STATE_RESOURCES);
        assertDoesNotThrow(() -> sut.verifyStateExists(Generator.randomItem(Generator.TERM_STATES)));
        verify(stateLanguageService).getTermStates();
    }

    @Test
    void verifyStateExistsThrowsInvalidLanguageConstantWhenUnknownStateIsPassedToService() {
        when(stateLanguageService.getTermStates()).thenReturn(TERM_STATE_RESOURCES);
        assertThrows(InvalidLanguageConstantException.class, () -> sut.verifyStateExists(Generator.generateUri()));
    }

    @Test
    void verifyStateExistsPassesWhenNullIsProvidedAsArgument() {
        assertDoesNotThrow(() -> sut.verifyStateExists(null));
        verify(stateLanguageService, never()).getTermStates();
    }
}

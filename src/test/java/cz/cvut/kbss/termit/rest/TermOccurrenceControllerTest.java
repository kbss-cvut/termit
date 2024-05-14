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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TermOccurrenceControllerTest extends BaseControllerTestRunner {

    private static final String LOCAL_NAME = "Occurrence-12345";
    private static final String NAMESPACE = Vocabulary.s_c_vyskyt_termu + "/";
    private static final URI OCCURRENCE_URI = URI.create(NAMESPACE + LOCAL_NAME);

    @Mock
    private TermOccurrenceService occurrenceService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private IdentifierResolver idResolverMock;

    @InjectMocks
    private TermOccurrenceController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void approveOccurrenceApprovesTermOccurrenceViaService() throws Exception {
        final TermOccurrence to = generateTermOccurrence();
        when(occurrenceService.getRequiredReference(OCCURRENCE_URI)).thenReturn(to);
        when(idResolverMock.resolveIdentifier(NAMESPACE, LOCAL_NAME)).thenReturn(OCCURRENCE_URI);
        mockMvc.perform(
                       put(TermOccurrenceController.PATH + "/" + LOCAL_NAME).queryParam(Constants.QueryParams.NAMESPACE,
                                                                                        NAMESPACE))
               .andExpect(status().isNoContent());
        verify(occurrenceService).getRequiredReference(OCCURRENCE_URI);
        verify(occurrenceService).approve(to);
    }

    private TermOccurrence generateTermOccurrence() {
        final Term term = Generator.generateTermWithId();
        final Term target = Generator.generateTermWithId();
        final TermOccurrence to = Generator.generateTermOccurrence(term, target, false);
        to.setUri(OCCURRENCE_URI);
        return to;
    }

    @Test
    void approveOccurrenceReturnsNotFoundWhenOccurrenceIsNotFoundByService() throws Exception {
        when(occurrenceService.getRequiredReference(OCCURRENCE_URI)).thenThrow(NotFoundException.class);
        when(idResolverMock.resolveIdentifier(NAMESPACE, LOCAL_NAME)).thenReturn(OCCURRENCE_URI);
        mockMvc.perform(
                       put(TermOccurrenceController.PATH + "/" + LOCAL_NAME).queryParam(Constants.QueryParams.NAMESPACE,
                                                                                        NAMESPACE))
               .andExpect(status().isNotFound());
        verify(occurrenceService, never()).approve(any());
    }

    @Test
    void removeOccurrenceRemovesTermOccurrenceViaService() throws Exception {
        final TermOccurrence to = generateTermOccurrence();
        when(occurrenceService.getRequiredReference(OCCURRENCE_URI)).thenReturn(to);
        when(idResolverMock.resolveIdentifier(NAMESPACE, LOCAL_NAME)).thenReturn(OCCURRENCE_URI);
        mockMvc.perform(
                       delete(TermOccurrenceController.PATH + "/" + LOCAL_NAME).queryParam(Constants.QueryParams.NAMESPACE,
                                                                                           NAMESPACE))
               .andExpect(status().isNoContent());
        verify(occurrenceService).getRequiredReference(OCCURRENCE_URI);
        verify(occurrenceService).remove(to);
    }

    @Test
    void createOccurrencePersistsSpecifiedTermOccurrence() throws Exception {
        final TermOccurrence to = generateTermOccurrence();
        mockMvc.perform(post(TermOccurrenceController.PATH).content(toJson(to)).contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        final ArgumentCaptor<TermOccurrence> captor = ArgumentCaptor.forClass(TermOccurrence.class);
        verify(occurrenceService).persist(captor.capture());
        assertEquals(to.getUri(), captor.getValue().getUri());
        assertEquals(to.getTerm(), captor.getValue().getTerm());
        assertEquals(to.getTarget().getSource(), captor.getValue().getTarget().getSource());
    }
}

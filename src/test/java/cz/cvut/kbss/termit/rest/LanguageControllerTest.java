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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LanguageControllerTest extends BaseControllerTestRunner {

    @Mock
    private LanguageService serviceMock;

    @InjectMocks
    private LanguageController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void getTermTypesReturnsLanguageOfTermTypes() throws Exception {
        final List<Term> types = List.of(Generator.generateTermWithId(), Generator.generateTermWithId());
        when(serviceMock.getTermTypes()).thenReturn(types);
        final MvcResult mvcResult = mockMvc.perform(get(LanguageController.PATH + "/types")).andExpect(status().isOk())
                                           .andReturn();
        assertEquals(types, readValue(mvcResult, new TypeReference<List<Term>>() {
        }));
        verify(serviceMock).getTermTypes();
    }

    @Test
    void getTermStatesReturnsLanguageOfTermStates() throws Exception {
        final List<RdfsResource> states = List.of(
                new RdfsResource(Generator.generateUri(), MultilingualString.create("Initial state", Environment.LANGUAGE), MultilingualString.create("Empty comment", Environment.LANGUAGE), Vocabulary.s_c_uvodni_stav_pojmu),
                new RdfsResource(Generator.generateUri(), MultilingualString.create("Terminal state", Environment.LANGUAGE), MultilingualString.create("Empty comment", Environment.LANGUAGE), Vocabulary.s_c_koncovy_stav_pojmu)
        );
        when(serviceMock.getTermStates()).thenReturn(states);
        final MvcResult mvcResult = mockMvc.perform(get(LanguageController.PATH + "/states")).andExpect(status().isOk())
                                           .andReturn();
        assertEquals(states, readValue(mvcResult, new TypeReference<List<RdfsResource>>() {
        }));
        verify(serviceMock).getTermStates();
    }
}

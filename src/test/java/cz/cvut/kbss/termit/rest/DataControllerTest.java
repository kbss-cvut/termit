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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DataControllerTest extends BaseControllerTestRunner {

    @Mock
    private DataRepositoryService dataServiceMock;

    @InjectMocks
    private DataController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void getPropertiesLoadsPropertiesFromDao() throws Exception {
        final RdfsResource property = create(Vocabulary.s_p_ma_krestni_jmeno, "Name", null);
        when(dataServiceMock.findAllProperties()).thenReturn(Collections.singletonList(property));
        final MvcResult mvcResult = mockMvc.perform(get("/data/properties")).andExpect(status().isOk()).andReturn();
        final List<RdfsResource> result = readValue(mvcResult, new TypeReference<List<RdfsResource>>() {
        });
        assertEquals(Collections.singletonList(property), result);
    }

    private static RdfsResource create(String uri, String label, String comment) {
        return new RdfsResource(URI.create(uri), new LangString(label, Environment.LANGUAGE),
                                comment != null ? new LangString(comment, Environment.LANGUAGE) : null, null);
    }

    @Test
    void getByIdReturnsResourceWithSpecifiedIdentifier() throws Exception {
        final RdfsResource property = create(Vocabulary.s_p_ma_krestni_jmeno, "Name", null);
        when(dataServiceMock.find(any())).thenReturn(Optional.of(property));
        final MvcResult mvcResult = mockMvc.perform(get("/data/resource").param("iri", property.getUri().toString()))
                                           .andExpect(status().isOk()).andReturn();
        assertEquals(property, readValue(mvcResult, RdfsResource.class));
    }

    @Test
    void getByIdThrowsNotFoundExceptionForUnknownResourceIdentifier() throws Exception {
        when(dataServiceMock.find(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/data/resource").param("iri", Generator.generateUri().toString()))
               .andExpect(status().isNotFound());
    }

    @Test
    void getLabelReturnsLabelOfResourceWithSpecifiedIdAsString() throws Exception {
        final URI uri = Generator.generateUri();
        final String label = "Test term";
        when(dataServiceMock.getLabel(uri, null)).thenReturn(Optional.of(label));
        final MvcResult mvcResult = mockMvc.perform(get("/data/label").param("iri", uri.toString()))
                                           .andExpect(status().isOk()).andReturn();
        assertEquals(label, readValue(mvcResult, String.class));
    }

    @Test
    void getLabelThrowsNotFoundExceptionWhenLabelIsNotFound() throws Exception {
        final URI uri = Generator.generateUri();
        when(dataServiceMock.getLabel(any(), any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/data/label").param("iri", uri.toString())).andExpect(status().isNotFound());
    }

    @Test
    void createPropertySavesResource() throws Exception {
        final RdfsResource property = create(RDFS.RANGE, "Range", "Property range");
        property.setTypes(Collections.singleton(RDF.PROPERTY));
        mockMvc.perform(
                       post("/data/properties").content(toJson(property)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
        verify(dataServiceMock).persistProperty(property);
    }

    @Test
    void createPropertyReturnsLocationHeaderLeadingToProperties() throws Exception {
        final RdfsResource property = create(RDFS.RANGE, "Range", "Property range");
        property.setTypes(Collections.singleton(RDF.PROPERTY));
        final MvcResult mvcResult = mockMvc.perform(
                                                   post("/data/properties").content(toJson(property)).contentType(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isCreated()).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION), containsString("/data/properties"));
    }
}

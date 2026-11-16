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

import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.rdf.Iri;
import cz.cvut.kbss.termit.dto.rdf.RdfTriple;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private IdentifierResolver identifierResolver;

    @InjectMocks
    private DataController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void getPropertiesLoadsPropertiesFromDao() throws Exception {
        final RdfsResource property = create(Vocabulary.s_p_has_first_name, "Name", null);
        when(dataServiceMock.findAllProperties()).thenReturn(Collections.singletonList(property));
        final MvcResult mvcResult = mockMvc.perform(get("/data/properties"))
                .andExpect(status().isOk())
                .andReturn();
        final List<RdfsResource> result = readValue(mvcResult, new TypeReference<>() {});
        assertEquals(Collections.singletonList(property), result);
    }

    private static RdfsResource create(String uri, String label, String comment) {
        return new RdfsResource(
                URI.create(uri),
                new LangString(label, Environment.LANGUAGE),
                comment != null ? new LangString(comment, Environment.LANGUAGE) : null,
                null);
    }

    @Test
    void getByIdReturnsResourceWithSpecifiedIdentifier() throws Exception {
        final RdfsResource property = create(Vocabulary.s_p_has_first_name, "Name", null);
        when(dataServiceMock.find(any())).thenReturn(Optional.of(property));
        final MvcResult mvcResult = mockMvc.perform(
                        get("/data/resource").param("iri", property.getUri().toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(property, readValue(mvcResult, RdfsResource.class));
    }

    @Test
    void getByIdThrowsNotFoundExceptionForUnknownResourceIdentifier() throws Exception {
        when(dataServiceMock.find(any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/data/resource")
                        .param("iri", Generator.generateUri().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLabelReturnsLabelOfResourceWithSpecifiedIdAsString() throws Exception {
        final URI uri = Generator.generateUri();
        final String label = "Test term";
        when(dataServiceMock.getLabel(uri, null)).thenReturn(Optional.of(label));
        final MvcResult mvcResult = mockMvc.perform(get("/data/label").param("iri", uri.toString()))
                .andExpect(status().isOk())
                .andReturn();
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
        mockMvc.perform(post("/data/properties")
                        .content(toJson(property))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated());
        verify(dataServiceMock).persist(property);
    }

    @Test
    void createPropertyReturnsLocationHeaderLeadingToProperties() throws Exception {
        final RdfsResource property = create(RDFS.RANGE, "Range", "Property range");
        property.setTypes(Collections.singleton(RDF.PROPERTY));
        final MvcResult mvcResult = mockMvc.perform(post("/data/properties")
                        .content(toJson(property))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION), containsString("/data/properties"));
    }

    @Test
    void getCustomAttributeUsageReturnsUsageTriples() throws Exception {
        final URI subject = Generator.generateUri();
        final URI predicate = Generator.generateUri();
        final URI object = Generator.generateUri();
        final URI context = Generator.generateUri();
        final RdfTriple triple = new RdfTriple(new Iri(subject), new Iri(predicate), new Iri(object), null);
        final RdfTriple nestedTriple = new RdfTriple(triple, new Iri(predicate), new Iri(object), new Iri(context));
        final List<RdfTriple> list = List.of(triple, nestedTriple);

        when(dataServiceMock.findCustomAttributeUsage(any(), any())).thenReturn(new PageImpl<>(list));

        final MvcResult mvcResult = mockMvc.perform(get("/data/custom-attributes/{localName}/usage", "attributeName")
                        .param("namespace", Generator.generateUriString()))
                .andExpect(status().isOk())
                .andReturn();

        final ArrayNode listResult = objectMapper
                .readTree(mvcResult.getResponse().getContentAsString())
                .asArray();

        final ObjectNode simpleTriple = listResult.get(0).asObject();
        assertEquals(objectMapper.writeValueAsString(triple), simpleTriple.toString());
        assertEquals("Triple", simpleTriple.get("type").asString());
        assertEquals("IRI", simpleTriple.get("subject").get("type").asString());
        assertEquals("IRI", simpleTriple.get("predicate").get("type").asString());
        assertEquals("IRI", simpleTriple.get("object").get("type").asString());
        assertTrue(simpleTriple.get("context").isNull());

        final ObjectNode resultNestedTriple = listResult.get(1).asObject();
        assertEquals(objectMapper.writeValueAsString(nestedTriple), resultNestedTriple.toString());
        assertEquals("Triple", resultNestedTriple.get("type").asString());
        assertEquals("Triple", resultNestedTriple.get("subject").get("type").asString());
        assertEquals("IRI", resultNestedTriple.get("predicate").get("type").asString());
        assertEquals("IRI", resultNestedTriple.get("object").get("type").asString());
        assertEquals(
                context.toString(),
                resultNestedTriple.get("context").get("value").asString());
    }
}

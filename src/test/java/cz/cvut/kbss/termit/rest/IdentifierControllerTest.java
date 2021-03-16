/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IdentifierControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/identifiers";

    @Mock
    private IdentifierResolver identifierResolverMock;

    @InjectMocks
    private IdentifierController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @AfterEach
    void tearDown() {
        Environment.resetCurrentUser();
    }

    @Test
    void generateIdentifierFailsWhenNoAssetTypeIsSpecified() throws Exception {
        final String label = "Metropolitan plan";
        mockMvc.perform(post(PATH)
                .param("name", label))
                .andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void generateIdentifierFailsWhenInvalidAssetTypeIsSpecified() throws Exception {
        final String label = "Metropolitan plan";
        mockMvc.perform(post(PATH)
                .param("name", label)
                .param("assetType", "INVALID")
        ).andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void generateIdentifierFailsWhenAssetTypeDoesNotSupportContextIri() throws Exception {
        final String label = "Metropolitan plan";
        mockMvc.perform(post(PATH)
                .param("name", label)
                .param("contextIri", "http://example.org/")
                .param("assetType", "VOCABULARY")
        ).andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void generateIdentifierFailsWhenAssetTypeRequiresContextIriButItIsNotProvided() throws Exception {
        final String label = "Metropolitan plan";
        mockMvc.perform(post(PATH)
                .param("name", label)
                .param("assetType", "FILE")
        ).andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void generateResourceIdentifierLetsServiceGenerateIdentifierUsingSpecifiedLabel()
            throws Exception {
        final String label = "Metropolitan plan";
        final URI uri = Generator.generateUri();
        when(identifierResolverMock.generateIdentifier(ConfigParam.NAMESPACE_RESOURCE, label)).thenReturn(uri);
        final MvcResult mvcResult = mockMvc.perform(post(PATH)
                .param("name", label)
                .param("assetType", "RESOURCE")
        )
                .andExpect(status().isOk()).andReturn();
        assertEquals(uri.toString(), readValue(mvcResult, String.class));
        verify(identifierResolverMock).generateIdentifier(ConfigParam.NAMESPACE_RESOURCE, label);
    }

    @Test
    void generateFileIdentifierLetsServiceGenerateIdentifierDerivedFromDocumentIdUsingSpecifiedLabel()
            throws Exception {
        final String label = "Metropolitan plan";
        final String name = "metropolitan-plan";
        final String documentName = "doc";
        final URI documentUri = URI.create(Environment.BASE_URI + "/" + documentName +
                Constants.DEFAULT_FILE_NAMESPACE_SEPARATOR + "/" + name);
        Generator.generateUri();
        final URI fileUri = URI.create(Environment.BASE_URI + "/" + documentName +
                Constants.DEFAULT_FILE_NAMESPACE_SEPARATOR + "/" + name);
        when(identifierResolverMock.generateDerivedIdentifier(any(), any(), any())).thenReturn(fileUri);

        final MvcResult mvcResult = mockMvc
                .perform(post(PATH)
                        .param("name", label)
                        .param("contextIri", documentUri.toString())
                        .param("assetType", "FILE")
                )
                .andExpect(status().isOk()).andReturn();
        final String result = readValue(mvcResult, String.class);
        assertEquals(fileUri.toString(), result);
        verify(identifierResolverMock).generateDerivedIdentifier(documentUri, ConfigParam.FILE_NAMESPACE_SEPARATOR, label);
    }

    @Test
    void generateVocabularyIdentifierLetsServiceGenerateIdentifierUsingSpecifiedLabel()
            throws Exception {
        final String label = "Metropolitan plan";
        final URI uri = Generator.generateUri();
        when(identifierResolverMock.generateIdentifier(ConfigParam.NAMESPACE_VOCABULARY, label)).thenReturn(uri);
        final MvcResult mvcResult = mockMvc.perform(post(PATH)
                .param("name", label)
                .param("assetType", "VOCABULARY")
        )
                .andExpect(status().isOk()).andReturn();
        assertEquals(uri.toString(), readValue(mvcResult, String.class));
        verify(identifierResolverMock).generateIdentifier(ConfigParam.NAMESPACE_VOCABULARY, label);
    }

    @Test
    void generateTermIdentifierLetsServiceGenerateIdentifierDerivedFromVocabularyIdUsingSpecifiedLabel()
            throws Exception {
        final String label = "Metropolitan plan";
        final String name = "metropolitan-plan";
        final String vocabularyName = "voc";
        final URI vocabularyUri = URI.create(Environment.BASE_URI + "/" + vocabularyName +
                Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR + "/" + name);
        Generator.generateUri();
        final URI termUri = URI.create(Environment.BASE_URI + "/" + vocabularyName +
                Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR + "/" + name);
        when(identifierResolverMock.generateDerivedIdentifier(any(), any(), any())).thenReturn(termUri);

        final MvcResult mvcResult = mockMvc
                .perform(post(PATH)
                        .param("name", label)
                        .param("contextIri", vocabularyUri.toString())
                        .param("assetType", "TERM")
                )
                .andExpect(status().isOk()).andReturn();
        final String result = readValue(mvcResult, String.class);
        assertEquals(termUri.toString(), result);
        verify(identifierResolverMock).generateDerivedIdentifier(vocabularyUri, ConfigParam.TERM_NAMESPACE_SEPARATOR, label);
    }
}

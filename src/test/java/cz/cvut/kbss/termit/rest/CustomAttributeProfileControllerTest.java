package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.dto.CustomAttributesProfileDescription;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.CustomAttributeConflictException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.CustomAttributeProfileService;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomAttributeProfileControllerTest extends BaseControllerTestRunner {

    @Mock
    private CustomAttributeProfileService profileService;

    @InjectMocks
    private CustomAttributeProfileController sut;

    @BeforeEach
    void setUp() {
        setUp(sut);
    }

    private void mockExportedProfile(CustomAttributesProfile profile) {
        when(profileService.exportProfile(any(), any()))
                .thenReturn(new TypeAwareByteArrayResource(
                        toJsonLd(profile).getBytes(StandardCharsets.UTF_8), JsonLd.MEDIA_TYPE, ".jsonld"));
    }

    private ResultActions requestImport(CustomAttributesProfile profile) throws Exception {
        return mockMvc.perform(post(CustomAttributeProfileController.PATH + "/import")
                .content(toJsonLd(profile))
                .contentType(JsonLd.MEDIA_TYPE));
    }

    private ResultActions requestExport(CustomAttributesProfileDescription profileDescription, Set<URI> attributeUris)
            throws Exception {
        final MockHttpServletRequestBuilder request = post(CustomAttributeProfileController.PATH + "/export")
                .content(toJsonLd(profileDescription))
                .contentType(JsonLd.MEDIA_TYPE);
        attributeUris.forEach(uri -> request.param("customAttributeUris", uri.toString()));
        return mockMvc.perform(request);
    }

    @Test
    void importCustomAttributesPassesProfileToService() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();

        requestImport(profile).andExpect(status().isNoContent());

        final ArgumentCaptor<CustomAttributesProfile> captor = ArgumentCaptor.forClass(CustomAttributesProfile.class);
        verify(profileService).importProfile(captor.capture());
        final CustomAttributesProfile result = captor.getValue();

        assertEquals(profile.getUri(), result.getUri());
        assertEquals(profile.getTitle(), result.getTitle());
        assertEquals(profile.getDescription(), result.getDescription());
        assertEquals(profile.getCustomAttributes(), result.getCustomAttributes());

        for (CustomAttribute expected : profile.getCustomAttributes()) {
            final CustomAttribute actual = result.getCustomAttributes().stream()
                    .filter(expected::equals)
                    .findFirst()
                    .orElseThrow();
            assertNotNull(actual);
        }
    }

    @Test
    void importCustomAttributesReturnsBadRequestWhenProfileHasNoCustomAttributes() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        profile.setCustomAttributes(Set.of());

        requestImport(profile).andExpect(status().isBadRequest());
        verify(profileService, never()).importProfile(any());
    }

    @Test
    void importCustomAttributesReturnsBadRequestWhenProfileHasNoIdentifier() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        profile.setUri(null);

        requestImport(profile).andExpect(status().isBadRequest());
        verify(profileService, never()).importProfile(any());
    }

    @Test
    void importCustomAttributesReturnsBadRequestWhenBodyIsNotValidJsonLd() throws Exception {
        mockMvc.perform(post(CustomAttributeProfileController.PATH + "/import")
                        .content("@prefix ex: <http://example.org/> .")
                        .contentType(JsonLd.MEDIA_TYPE))
                .andExpect(status().isBadRequest());
        verify(profileService, never()).importProfile(any());
    }

    @Test
    void importCustomAttributesReturnsUnprocessableContentWhenExistingAttributeConflictsWithProfile() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        doThrow(new CustomAttributeConflictException(false, true, Generator.generateUri()))
                .when(profileService)
                .importProfile(any());

        requestImport(profile).andExpect(status().isUnprocessableContent());
    }

    @Test
    void exportCustomAttributesPassesProfileDescriptionAndAttributeIdentifiersToService() throws Exception {
        final CustomAttributesProfileDescription description = Generator.generateCustomAttributeProfileDescription();
        description.getTitle().set("cs", "Testovací profil");
        final Set<URI> attributeUris = Set.of(Generator.generateUri(), Generator.generateUri());
        mockExportedProfile(Generator.generateCustomAttributeProfile());

        requestExport(description, attributeUris).andExpect(status().isOk());

        final ArgumentCaptor<CustomAttributesProfileDescription> captor =
                ArgumentCaptor.forClass(CustomAttributesProfileDescription.class);
        verify(profileService).exportProfile(captor.capture(), eq(attributeUris));
        assertEquals(description.getUri(), captor.getValue().getUri());
        assertEquals(description.getTitle(), captor.getValue().getTitle());
        assertEquals(description.getDescription(), captor.getValue().getDescription());
    }

    @Test
    void exportCustomAttributesReturnsProfileAsAttachmentNamedAfterProfileIdentifier() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        mockExportedProfile(profile);
        final String expectedContent = toJsonLd(profile);

        final String expectedFileName = IdentifierResolver.extractIdentifierFragment(profile.getUri());

        final CustomAttributesProfileDescription description = new CustomAttributesProfileDescription(profile);

        MvcResult mvcResult = requestExport(description, Set.of(Generator.generateUri()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(JsonLd.MEDIA_TYPE, mvcResult.getResponse().getContentType());
        assertEquals(expectedContent, mvcResult.getResponse().getContentAsString());
        final String disposition = mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertThat(disposition, containsString("attachment"));
        assertThat(disposition, containsString("filename=\"" + expectedFileName + ".jsonld\""));
    }

    @Test
    void exportCustomAttributesReturnsBadRequestWhenAttributeIdentifiersAreMissing() throws Exception {
        requestExport(Generator.generateCustomAttributeProfileDescription(), Set.of())
                .andExpect(status().isBadRequest());
        verify(profileService, never()).exportProfile(any(), any());
    }

    @Test
    void exportCustomAttributesReturnsBadRequestWhenBodyIsNotJsonLd() throws Exception {
        mockMvc.perform(post(CustomAttributeProfileController.PATH + "/export")
                        .content("@prefix ex: <http://example.org/> .")
                        .contentType(JsonLd.MEDIA_TYPE)
                        .param("customAttributeUris", Generator.generateUri().toString()))
                .andExpect(status().isBadRequest());
        verify(profileService, never()).exportProfile(any(), any());
    }

    @Test
    void exportCustomAttributesReturnsBadRequestWhenProfileIdentifierIsMissing() throws Exception {
        final CustomAttributesProfileDescription description = Generator.generateCustomAttributeProfileDescription();
        description.setUri(null);

        requestExport(description, Set.of(Generator.generateUri())).andExpect(status().isBadRequest());
        verify(profileService, never()).exportProfile(any(), any());
    }

    @Test
    void exportCustomAttributesReturnsBadRequestWhenProfileTitleIsMissing() throws Exception {
        final CustomAttributesProfileDescription description = Generator.generateCustomAttributeProfileDescription();
        description.setTitle(null);

        requestExport(description, Set.of(Generator.generateUri())).andExpect(status().isBadRequest());
        verify(profileService, never()).exportProfile(any(), any());
    }
}

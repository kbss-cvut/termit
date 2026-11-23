package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.CustomAttributeConflictException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.service.business.CustomAttributeProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private ResultActions requestImport(CustomAttributesProfile profile) throws Exception {
        return mockMvc.perform(post(CustomAttributeProfileController.PATH + "/import")
                .content(toJsonLd(profile))
                .contentType(JsonLd.MEDIA_TYPE));
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
    void importCustomAttributesReturnsConflictWhenExistingAttributeConflictsWithProfile() throws Exception {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        doThrow(new CustomAttributeConflictException(false, true, Generator.generateUri()))
                .when(profileService)
                .importProfile(any());

        requestImport(profile).andExpect(status().isConflict());
    }
}

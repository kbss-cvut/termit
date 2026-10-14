package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jopa.vocabulary.XSD;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.CustomAttributesProfile;
import cz.cvut.kbss.termit.dto.CustomAttributesProfileDescription;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.CustomAttributeConflictException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAttributeProfileServiceTest {

    @Mock
    private DataRepositoryService dataService;

    private CustomAttributeProfileService sut;

    @BeforeEach
    void setUp() {
        this.sut = new CustomAttributeProfileService(dataService, Environment.getJsonLdObjectMapper());
    }

    private static CustomAttribute copyOf(CustomAttribute attribute) {
        final CustomAttribute copy =
                new CustomAttribute(attribute.getUri(), attribute.getLabel(), attribute.getComment());
        copy.setDomain(attribute.getDomain());
        copy.setRange(attribute.getRange());
        copy.setAnnotatedRelationships(attribute.getAnnotatedRelationships());
        return copy;
    }

    private static void assertAttributeEquals(CustomAttribute expected, CustomAttribute actual) {
        assertEquals(expected, actual);
        assertEquals(expected.getComment(), actual.getComment());
        assertEquals(expected.getDomain(), actual.getDomain());
        assertEquals(expected.getRange(), actual.getRange());
        assertEquals(expected.getAnnotatedRelationships(), actual.getAnnotatedRelationships());
    }

    @Test
    void exportProfileReturnsJsonLdResource() {
        final CustomAttribute attribute = Generator.generateCustomAttribute();
        when(dataService.findCustomAttributes(Set.of(attribute.getUri()))).thenReturn(List.of(attribute));

        final TypeAwareResource result =
                sut.exportProfile(Generator.generateCustomAttributeProfileDescription(), Set.of(attribute.getUri()));
        assertEquals(Optional.of(JsonLd.MEDIA_TYPE), result.getMediaType());
        assertEquals(Optional.of(".jsonld"), result.getFileExtension());
    }

    @Test
    void exportProfileExportsProfileDescriptionWithSpecifiedCustomAttributes() throws Exception {
        final CustomAttributesProfileDescription description = Generator.generateCustomAttributeProfileDescription();
        final List<CustomAttribute> attributes =
                List.of(Generator.generateCustomAttribute(), Generator.generateCustomAttribute());
        final Set<URI> attributeUris =
                attributes.stream().map(HasIdentifier::getUri).collect(Collectors.toUnmodifiableSet());

        when(dataService.findCustomAttributes(attributeUris)).thenReturn(attributes);

        final TypeAwareResource resource = sut.exportProfile(description, attributeUris);

        final CustomAttributesProfile result = Environment.getJsonLdObjectMapper()
                .readValue(resource.getContentAsByteArray(), CustomAttributesProfile.class);
        assertEquals(description.getUri(), result.getUri());
        assertEquals(description.getTitle(), result.getTitle());
        assertEquals(description.getDescription(), result.getDescription());
        assertEquals(attributes.size(), result.getCustomAttributes().size());
        for (CustomAttribute expected : attributes) {
            final CustomAttribute actual = result.getCustomAttributes().stream()
                    .filter(expected::equals)
                    .findFirst()
                    .orElseThrow();
            assertAttributeEquals(expected, actual);
        }
    }

    @Test
    void importProfilePersistsAttributesWhichDoNotExistYet() {
        final CustomAttribute first = Generator.generateCustomAttribute();
        final CustomAttribute second = Generator.generateCustomAttribute();

        when(dataService.findCustomAttribute(any())).thenReturn(Optional.empty());

        sut.importProfile(profileOf(first, second));

        verify(dataService).persistCustomAttribute(first);
        verify(dataService).persistCustomAttribute(second);
        verify(dataService, never()).updateCustomAttribute(any());
    }

    @Test
    void importProfileUpdatesAttributesWhichAlreadyExist() {
        final CustomAttribute existing = Generator.generateCustomAttribute();
        final CustomAttribute updated = copyOf(existing);
        updated.setLabel(MultilingualString.create("Updated attribute", Environment.LANGUAGE));
        final CustomAttribute added = Generator.generateCustomAttribute();

        when(dataService.findCustomAttribute(existing.getUri())).thenReturn(Optional.of(existing));
        when(dataService.findCustomAttribute(added.getUri())).thenReturn(Optional.empty());

        sut.importProfile(profileOf(added, updated));

        verify(dataService).updateCustomAttribute(updated);
        verify(dataService, never()).persistCustomAttribute(updated);
        verify(dataService).persistCustomAttribute(added);
        verify(dataService, never()).updateCustomAttribute(added);
    }

    @Test
    void importProfileThrowsCustomAttributeConflictExceptionWhenExistingAttributeHasDifferentDomain() {
        final CustomAttribute existing = Generator.generateCustomAttribute();
        final CustomAttribute imported = copyOf(existing);
        imported.setDomain(URI.create(SKOS.CONCEPT_SCHEME));

        assertNotEquals(existing.getDomain(), imported.getDomain());
        when(dataService.findCustomAttribute(existing.getUri())).thenReturn(Optional.of(existing));

        assertThrows(CustomAttributeConflictException.class, () -> sut.importProfile(profileOf(imported)));
        verify(dataService, never()).persistCustomAttribute(any());
        verify(dataService, never()).updateCustomAttribute(any());
    }

    @Test
    void importProfileThrowsCustomAttributeConflictExceptionWhenExistingAttributeHasDifferentRange() {
        final CustomAttribute existing = Generator.generateCustomAttribute();
        final CustomAttribute imported = copyOf(existing);
        imported.setRange(URI.create(XSD.INT));

        assertNotEquals(existing.getRange(), imported.getRange());
        when(dataService.findCustomAttribute(existing.getUri())).thenReturn(Optional.of(existing));

        assertThrows(CustomAttributeConflictException.class, () -> sut.importProfile(profileOf(imported)));
        verify(dataService, never()).persistCustomAttribute(any());
        verify(dataService, never()).updateCustomAttribute(any());
    }

    @Test
    void importCustomAttributesReturnsBadRequestWhenCustomAttributesAreMissingIdentifiers() {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        profile.getCustomAttributes().forEach(a -> a.setUri(null));

        final Exception e = assertThrows(NullPointerException.class, () -> sut.importProfile(profile));
        assertEquals("Custom Attribute must have an identifier!", e.getMessage());
    }

    static CustomAttributesProfile profileOf(CustomAttribute... attributes) {
        final CustomAttributesProfile profile = Generator.generateCustomAttributeProfile();
        profile.setCustomAttributes(Set.of(attributes));
        return profile;
    }
}

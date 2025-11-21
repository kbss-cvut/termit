package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UnsupportedDomainException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRepositoryServiceTest {

    @Mock
    private DataDao dataDao;

    @Spy
    final Configuration config = new Configuration();

    @Spy
    final IdentifierResolver idResolver = new IdentifierResolver(config);

    @InjectMocks
    private DataRepositoryService sut;

    @BeforeEach
    void setUp() {
        config.getPersistence().setLanguage(Environment.LANGUAGE);
    }

    @Test
    void persistCustomAttributeSetsSkosConceptAsAttributeDomainWhenNoDomainIsSet() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));
        customAttribute.setUri(Generator.generateUri());

        sut.persistCustomAttribute(customAttribute);
        assertEquals(URI.create(SKOS.CONCEPT), customAttribute.getDomain());
    }

    @ParameterizedTest
    @CsvSource({SKOS.CONCEPT, Vocabulary.s_c_slovnik})
    void persistCustomAttributeSupportsSelectedDomains(String domain) {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));
        customAttribute.setUri(Generator.generateUri());
        customAttribute.setDomain(URI.create(domain));

        sut.persistCustomAttribute(customAttribute);
        verify(dataDao).persist(customAttribute);
        assertEquals(URI.create(domain), customAttribute.getDomain());
    }

    @Test
    void persistCustomAttributeThrowsUnsupportedDomainExceptionWhenDomainIsNotSkosConceptOrVocabulary() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));
        customAttribute.setUri(Generator.generateUri());
        customAttribute.setDomain(URI.create("https://example.com/unsupported-domain"));

        assertThrows(UnsupportedDomainException.class, () -> sut.persistCustomAttribute(customAttribute));
    }

    @Test
    void persistCustomAttributeGeneratesAttributeUriWhenItIsNotSet() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));

        sut.persistCustomAttribute(customAttribute);
        assertNotNull(customAttribute.getUri());
        assertEquals(URI.create(Vocabulary.s_c_vlastni_atribut + "/custom-attribute"), customAttribute.getUri());
    }

    @Test
    void persistCustomAttributeUsesAnyAvailableLabelToGenerateUriWhenLabelInConfiguredLanguageIsNotSet() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Testovací atribut", "cs"));
        assertEquals(Environment.LANGUAGE, config.getPersistence().getLanguage());

        sut.persistCustomAttribute(customAttribute);
        assertNotNull(customAttribute.getUri());
        assertThat(customAttribute.getUri().toString(), not(containsString("null")));
        assertEquals(URI.create(Vocabulary.s_c_vlastni_atribut + "/testovací-atribut"), customAttribute.getUri());
    }

    @Test
    void persistCustomAttributeThrowsValidationExceptionWhenLabelIsEmpty() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(new MultilingualString(Map.of()));

        assertThrows(ValidationException.class, () -> sut.persistCustomAttribute(customAttribute));
    }

    @Test
    void updateCustomAttributeUpdatesLabelAndDescriptionOfExistingCustomAttribute() {
        final CustomAttribute existing = new CustomAttribute(Generator.generateUri(),
                                                             MultilingualString.create("Attribute one", "en"),
                                                             MultilingualString.create("Description one", "en"));
        existing.setDomain(URI.create(SKOS.CONCEPT));
        existing.setRange(URI.create(SKOS.CONCEPT));
        when(dataDao.findCustomAttribute(existing.getUri())).thenReturn(Optional.of(existing));
        final CustomAttribute updated = new CustomAttribute(existing.getUri(),
                                                            MultilingualString.create("Updated attribute", "en"),
                                                            MultilingualString.create("Updated description", "en"));
        updated.setDomain(URI.create(SKOS.CONCEPT));
        updated.setRange(URI.create("http://www.w3.org/2001/XMLSchema#string"));
        updated.setAnnotatedRelationships(Set.of(URI.create("http://example.org/property")));

        sut.updateCustomAttribute(updated);
        assertEquals(updated.getLabel(), existing.getLabel());
        assertEquals(updated.getComment(), existing.getComment());
        assertEquals(updated.getDomain(), existing.getDomain());
        assertEquals(updated.getRange(), existing.getRange());
        assertEquals(updated.getAnnotatedRelationships(), existing.getAnnotatedRelationships());
    }
}

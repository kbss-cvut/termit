package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void persistCustomAttributeSetsSkosConceptAsAttributeDomain() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));
        customAttribute.setUri(Generator.generateUri());

        sut.persistCustomAttribute(customAttribute);
        assertEquals(URI.create(SKOS.CONCEPT), customAttribute.getDomain());
    }

    @Test
    void persistCustomAttributeGeneratesAttributeUriWhenItIsNotSet() {
        final CustomAttribute customAttribute = new CustomAttribute();
        customAttribute.setLabel(MultilingualString.create("Custom attribute", "en"));

        sut.persistCustomAttribute(customAttribute);
        assertNotNull(customAttribute.getUri());
        assertEquals(URI.create(Vocabulary.s_c_vlastni_atribut + "/custom-attribute"), customAttribute.getUri());
    }
}

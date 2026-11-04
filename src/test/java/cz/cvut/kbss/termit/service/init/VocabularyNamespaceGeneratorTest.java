package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyNamespaceGeneratorTest extends BaseServiceTestRunner {
    @Autowired
    VocabularyDao dao;

    @Autowired
    VocabularyNamespaceGenerator sut;

    @Test
    void generatesNamespaceForVocabularyWithoutNamespace() {
        final Vocabulary withoutNamespace = Generator.generateVocabularyWithId();
        withoutNamespace.setPreferredNamespaceUri(null);
        transactional(() -> dao.persist(withoutNamespace));

        assertNull(transactional(() -> dao.find(withoutNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri());

        sut.generateMissingNamespaceUris();

        final String generatedNamespace = transactional(
                        () -> dao.find(withoutNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri();
        assertTrue(StringUtils.hasText(generatedNamespace));
    }

    @Test
    void generateDoesNotChangeExistingNamespaces() {
        final Vocabulary withNamespace = Generator.generateVocabularyWithId();
        transactional(() -> dao.persist(withNamespace));

        final String originalNamespace = withNamespace.getPreferredNamespaceUri();
        assertNotNull(originalNamespace);

        sut.generateMissingNamespaceUris();

        final String currentNamespace = transactional(
                        () -> dao.find(withNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri();
        assertEquals(originalNamespace, currentNamespace);
    }

    @Test
    void doesNotGenerateNamespaceForSnapshot() {
        final Vocabulary withoutNamespace = Generator.generateVocabularyWithId();
        withoutNamespace.setPreferredNamespaceUri(null);
        withoutNamespace.setTypes(Set.of(cz.cvut.kbss.termit.util.Vocabulary.s_c_version_of_vocabulary));
        transactional(() -> dao.persist(withoutNamespace));

        assertNull(transactional(() -> dao.find(withoutNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri());

        sut.generateMissingNamespaceUris();

        final String generatedNamespace = transactional(
                        () -> dao.find(withoutNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri();
        assertNull(generatedNamespace);
    }

    @Test
    void doesNotChangeExistingNamespacesForSnapshot() {
        final Vocabulary withNamespace = Generator.generateVocabularyWithId();
        withNamespace.setTypes(Set.of(cz.cvut.kbss.termit.util.Vocabulary.s_c_version_of_vocabulary));
        transactional(() -> dao.persist(withNamespace));

        final String originalNamespace = withNamespace.getPreferredNamespaceUri();
        assertNotNull(originalNamespace);

        sut.generateMissingNamespaceUris();

        final String currentNamespace = transactional(
                        () -> dao.find(withNamespace.getUri()).orElseThrow())
                .getPreferredNamespaceUri();
        assertEquals(originalNamespace, currentNamespace);
    }
}

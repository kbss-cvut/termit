package cz.cvut.kbss.termit.persistence.namespace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyNamespaceResolverTest {

    @Test
    void setVocabularyPreferredNamespaceSetsVocabularyNamespaceBasedOnItsIdentifierAndTermSeparator() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary);
        assertTrue(vocabulary.hasUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
        assertEquals(Set.of(vocabulary.getUri() + "/term/"),
                     vocabulary.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
    }

    @Test
    void setVocabularyPreferredNamespaceDoesNothingWhenVocabularyAlreadyHasPreferredNamespace() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.addUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri,
                                            vocabulary.getUri() + "/");
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary);
        assertTrue(vocabulary.hasUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
        assertEquals(Set.of(vocabulary.getUri() + "/"),
                     vocabulary.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
    }

    @Test
    void setVocabularyPreferredNamespaceThrowsIllegalArgumentExceptionWhenVocabularyDoesNotHaveIdentifier() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, new Configuration());
        assertThrows(IllegalArgumentException.class, () -> sut.setVocabularyPreferredNamespace(vocabulary));
    }

    @Test
    void setVocabularyPreferredNamespaceWithValueSetsSpecifiedValueAsVocabularyNamespace() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final String namespace = vocabulary.getUri() + "/";
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary, namespace);
        assertEquals(Set.of(namespace),
                     vocabulary.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
    }

    @Test
    void setVocabularyPreferredNamespaceWithValueFallsBackToConfigBasedWhenNullIsProvidedAsNamespace() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary, null);
        assertTrue(vocabulary.hasUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
        assertEquals(Set.of(vocabulary.getUri() + "/term/"),
                     vocabulary.getProperties().get(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri));
    }
}

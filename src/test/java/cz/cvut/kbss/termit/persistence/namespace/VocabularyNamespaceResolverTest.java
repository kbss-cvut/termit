package cz.cvut.kbss.termit.persistence.namespace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VocabularyNamespaceResolverTest {

    @Test
    void setVocabularyPreferredNamespaceSetsVocabularyNamespaceBasedOnItsIdentifierAndTermSeparator() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary);
        assertEquals(vocabulary.getUri() + "/term/",vocabulary.getPreferredNamespaceUri());
    }

    @Test
    void setVocabularyPreferredNamespaceDoesNothingWhenVocabularyAlreadyHasPreferredNamespace() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setPreferredNamespaceUri(vocabulary.getUri() + "/");
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary);
        assertEquals(vocabulary.getUri() + "/",vocabulary.getPreferredNamespaceUri());
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
        assertEquals(namespace, vocabulary.getPreferredNamespaceUri());
    }

    @Test
    void setVocabularyPreferredNamespaceWithValueFallsBackToConfigBasedWhenNullIsProvidedAsNamespace() {
        final Configuration config = new Configuration();
        config.getNamespace().getTerm().setSeparator("/term");
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final VocabularyNamespaceResolver sut = new VocabularyNamespaceResolver(null, config);

        sut.setVocabularyPreferredNamespace(vocabulary, null);
        assertEquals(vocabulary.getUri() + "/term/", vocabulary.getPreferredNamespaceUri());
    }
}

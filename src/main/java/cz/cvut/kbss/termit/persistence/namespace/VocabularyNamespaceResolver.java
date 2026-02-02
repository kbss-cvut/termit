package cz.cvut.kbss.termit.persistence.namespace;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Objects;

/**
 * Resolves and sets the preferred namespace of a vocabulary.
 */
@CacheConfig(cacheNames = "vocabularyNamespace")
@Component
public class VocabularyNamespaceResolver {

    private final VocabularyDao vocabularyDao;

    private final Configuration.Namespace.NamespaceDetail namespaceConfig;

    public VocabularyNamespaceResolver(VocabularyDao vocabularyDao, Configuration configuration) {
        this.vocabularyDao = vocabularyDao;
        this.namespaceConfig = configuration.getNamespace().getTerm();
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#vocabularyUri", cacheNames = "vocabularyNamespace")
    @Nonnull
    public String resolveNamespace(@Nonnull URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return vocabularyDao.getPreferredNamespace(vocabularyUri)
                            .orElseGet(() -> vocabularyUri + namespaceConfig.getSeparator());
    }

    /**
     * Sets preferred namespace on the specified vocabulary (if not already configured).
     * <p>
     * The namespace is based on the vocabulary identifier and the configured term identifier separator
     * ({@link Configuration#getNamespace()}). Therefore, it is assumed the vocabulary identifier is set.
     * <p>
     * If the vocabulary already has a preferred namespace, this method does nothing.
     *
     * @param vocabulary Vocabulary to set preferred namespace on
     */
    public void setVocabularyPreferredNamespace(@Nonnull Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        if (vocabulary.getUri() == null) {
            throw new IllegalArgumentException("Vocabulary " + vocabulary + " is missing identifier.");
        }
        if (!vocabulary.hasUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri)) {
            String configuredSep = namespaceConfig.getSeparator();
            vocabulary.addUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri,
                                                IdentifierResolver.ensureNamespaceSeparatorTermination(
                                                        vocabulary.getUri() + configuredSep));
        }
    }

    /**
     * Sets the specified value (if present) as the preferred namespace of the specified vocabulary.
     * <p>
     * If the value is not present, {@link #setVocabularyPreferredNamespace(Vocabulary)} is used to resolve and set the
     * namespace.
     * <p>
     * If the vocabulary already has a preferred namespace, this method does nothing.
     *
     * @param vocabulary Vocabulary to set preferred namespace on
     * @param namespace  Namespace to set, possibly {@code null}
     */
    public void setVocabularyPreferredNamespace(@Nonnull Vocabulary vocabulary, @Nullable String namespace) {
        Objects.requireNonNull(vocabulary);
        if (namespace != null && !vocabulary.hasUnmappedPropertyValue(
                cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri)) {
            vocabulary.addUnmappedPropertyValue(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri,
                                                namespace);
        } else {
            setVocabularyPreferredNamespace(vocabulary);
        }
    }
}

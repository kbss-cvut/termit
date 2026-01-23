package cz.cvut.kbss.termit.service.namespace;

import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Configuration;
import jakarta.annotation.Nonnull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

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
        return vocabularyDao.getPreferredNamespace(vocabularyUri)
                            .orElseGet(() -> vocabularyUri + namespaceConfig.getSeparator());
    }
}

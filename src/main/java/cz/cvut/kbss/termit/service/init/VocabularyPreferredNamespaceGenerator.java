package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyPreferredNamespaceGenerator {

    private final VocabularyDao vocabularyDao;

    private final VocabularyNamespaceResolver vocabularyNamespaceResolver;

    public VocabularyPreferredNamespaceGenerator(VocabularyDao vocabularyDao,
                                                 VocabularyNamespaceResolver vocabularyNamespaceResolver) {
        this.vocabularyDao = vocabularyDao;
        this.vocabularyNamespaceResolver = vocabularyNamespaceResolver;
    }

    @Async
    @Transactional
    void generatePreferredNamespace() {
        vocabularyDao.findAll().forEach(vocabularyNamespaceResolver::setVocabularyPreferredNamespace);
    }
}

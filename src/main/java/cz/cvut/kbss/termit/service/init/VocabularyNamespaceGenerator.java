package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class VocabularyNamespaceGenerator {
    private final VocabularyNamespaceResolver namespaceResolver;
    private final EntityManager em;
    private final VocabularyDao vocabularyDao;

    public VocabularyNamespaceGenerator(
            VocabularyNamespaceResolver namespaceResolver, EntityManager em, VocabularyDao vocabularyDao) {
        this.namespaceResolver = namespaceResolver;
        this.em = em;
        this.vocabularyDao = vocabularyDao;
    }

    @Transactional
    public void generateMissingNamespaceUris() {
        forEachWithoutNamespace(namespaceResolver::setVocabularyPreferredNamespace);
    }

    private void forEachWithoutNamespace(Consumer<Vocabulary> consumer) {
        try (Stream<URI> stream = em.createNativeQuery("""
                SELECT ?v WHERE {
                    ?v a ?conceptScheme .
                    FILTER NOT EXISTS {
                        ?v a ?snapshot .
                    }
                    FILTER NOT EXISTS {
                        ?v ?hasNamespace ?ns .
                    }
                }
            """, URI.class)
                .setParameter("conceptScheme", Vocabulary_.entityClassIRI)
                .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_version_of_vocabulary))
                .setParameter("hasNamespace", Vocabulary_.preferredNamespaceUriPropertyIRI)
                .getResultStream()) {

            stream.map(vocabularyDao::find).map(Optional::orElseThrow).forEach(consumer);
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to find all vocabularies without a namespace", e);
        }
    }
}

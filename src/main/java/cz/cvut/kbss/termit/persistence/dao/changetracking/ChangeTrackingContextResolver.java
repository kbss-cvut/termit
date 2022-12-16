package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

/**
 * Determines repository context into which change tracking records are stored.
 */
@Component
public class ChangeTrackingContextResolver {

    private final EntityManager em;

    private final String contextExtension;

    private final EditableVocabularies vocabularies;

    @Autowired
    public ChangeTrackingContextResolver(EntityManager em, Configuration config, EditableVocabularies vocabularies) {
        this.em = em;
        this.contextExtension = config.getChangetracking().getContext().getExtension();
        this.vocabularies = vocabularies;
    }

    /**
     * Resolves change tracking context of the specified changed asset.
     * <p>
     * In general, each vocabulary has its own change tracking context, so changes to it and all its terms are stored in
     * this context.
     *
     * @param changedAsset Asset for which change records will be generated
     * @return Identifier of the change tracking context of the specified asset
     */
    public URI resolveChangeTrackingContext(Asset<?> changedAsset) {
        Objects.requireNonNull(changedAsset);
        if (changedAsset instanceof Vocabulary) {
            URI changeTrackingContextURI = resolveExistingChangeTrackingContext(changedAsset.getUri());
            if(Objects.nonNull(changeTrackingContextURI))
                return changeTrackingContextURI;
            return URI.create(changedAsset.getUri().toString().concat(contextExtension));
        } else if (changedAsset instanceof Term) {
            URI changeTrackingContextURI = resolveExistingChangeTrackingContext(resolveTermVocabulary((Term) changedAsset));
            if(Objects.nonNull(changeTrackingContextURI))
                return changeTrackingContextURI;
            return URI.create(resolveTermVocabulary((Term) changedAsset).toString().concat(contextExtension));
        }
        return URI.create(changedAsset.getUri().toString().concat(contextExtension));
    }

    private URI resolveTermVocabulary(Term term) {
        if (term.getGlossary() != null) {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?v ?hasGlossary ?glossary . }", URI.class)
                     .setParameter("hasGlossary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                     .setParameter("glossary", term.getGlossary()).getSingleResult();
        } else {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?t ?inVocabulary ?v . }", URI.class)
                     .setParameter("inVocabulary",
                             URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("t", term).getSingleResult();
        }
    }

    private URI resolveExistingChangeTrackingContext(URI vocabularyURI) {
        Optional<URI> vocabularyContext = vocabularies.getVocabularyContext(vocabularyURI);
        return vocabularyContext.map(uri -> em.createNativeQuery(
                                                      "SELECT DISTINCT ?ctc WHERE { GRAPH ?vc { ?vc ?hasChangeTrackingContext ?ctc } }", URI.class)
                                              .setParameter("vc", uri)
                                              .setParameter("hasChangeTrackingContext",
                                                      URI.create(
                                                              cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_kontext_sledovani_zmen))
                                              .getSingleResult()).orElse(null);
    }
}

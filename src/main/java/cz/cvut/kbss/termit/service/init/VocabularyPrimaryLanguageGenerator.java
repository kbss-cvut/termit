package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class VocabularyPrimaryLanguageGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyPrimaryLanguageGenerator.class);

    private final EntityManager em;
    private final Configuration.Persistence config;

    public VocabularyPrimaryLanguageGenerator(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config.getPersistence();
    }

    /**
     * Creates primary language for vocabularies that do not have it.
     * When the {@link cz.cvut.kbss.termit.model.Vocabulary#primaryLanguage Vocabulary#primaryLanguage} is missing,
     * it is set to the instance language defined by {@link Configuration.Persistence#language}.
     * Vocabularies with an existing primary language are not modified.
     * <p>
     * This method is not asynchronous since vocabularies without language are invalid.
     */
    @Transactional
    public void generateMissingPrimaryLanguage() {
        LOG.debug("Generating missing primary languages for vocabularies.");
        em.createNativeQuery("""
                    INSERT {
                        GRAPH ?vocabulary {
                            ?vocabulary ?hasLanguage ?language .
                        }
                    } WHERE {
                        ?vocabulary a ?vocabularyType .
                        FILTER NOT EXISTS {
                            ?vocabulary ?hasLanguage ?existingLanguage .
                        }
                    }
                    """)
          .setParameter("vocabularyType", URI.create(Vocabulary.s_c_slovnik))
          .setParameter("hasLanguage", URI.create(DC.Terms.LANGUAGE))
          .setParameter("language", config.getLanguage(), null)
          .executeUpdate();
        em.flush();
    }
}

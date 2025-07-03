package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

public class SparqlPatterns {
    /**
     * Inserts a SPARQL pattern binding the {@code ?vocabulary} of the {@code ?entity}.
     * @param entity the variable representing the entity e.g. {@code "?entity"}
     * @return the pattern to be inserted into a SPARQL query
     * @implSpec Requires parameters bound by {@link #bindVocabularyRelatedParameters(Query)}
     */
    public static String insertVocabularyPattern(String entity) {
        return """
            OPTIONAL { #entity ?isFromVocabulary ?vocabulary . }
            OPTIONAL { #entity ?hasVocabulary ?vocabulary . }
            OPTIONAL { #entity ?inDocument/?hasVocabulary ?vocabulary . }
            OPTIONAL {
                #entity a ?vocabularyType .
                BIND(#entity AS ?vocabulary)
            }
            """.replace("#entity", entity);
    }

    /**
     * Binds parameters for {@link #insertVocabularyPattern(String)}
     */
    public static void bindVocabularyRelatedParameters(Query query) {
        query
                .setParameter("isFromVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                .setParameter("hasVocabulary", URI.create(Vocabulary.s_p_ma_dokumentovy_slovnik))
                .setParameter("inDocument", URI.create(Vocabulary.s_p_je_casti_dokumentu))
                .setParameter("vocabularyType", URI.create(Vocabulary.s_c_slovnik));
    }

    /**
     * Inserts a SPARQL pattern binding {@code ?language} to the language of the {@code entity} or its {@code ?vocabulary}.
     * The language of the entity is prioritized.
     * If the language is not found, the {@code ?language} variable remains unchanged.
     * @param entity the variable representing the entity e.g. {@code "?entity"}
     * @return the pattern to be inserted into a SPARQL query
     * @implSpec Requires {@code ?hasLanguage} to be bound to {@link DC.Terms#LANGUAGE} and {@code ?language} to be an actual variable (cannot be a query parameter).
     */
    public static String insertLanguagePattern(String entity) {
        return """
                OPTIONAL {
                    #entity ?hasLanguage ?entityLanguage .
                }
                OPTIONAL {
                    ?vocabulary ?hasLanguage ?vocabularyLanguage .
                }
                BIND (COALESCE(?entityLanguage, ?vocabularyLanguage, ?language) as ?language) .
                """.replace("#entity", entity);
    }


    private SparqlPatterns() {
        throw new AssertionError();
    }
}

package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

/**
 * Utility functions for SPARQL query construction.
 */
public class SparqlPatterns {
    /**
     * Inserts a SPARQL pattern binding the {@code ?vocabulary} of the {@code ?entity}.
     *
     * @param entity the variable representing the entity e.g. {@code "?entity"}
     * @return the pattern to be inserted into a SPARQL query
     * @implSpec Requires parameters bound by {@link #bindVocabularyRelatedParameters(Query)}
     */
    public static String insertVocabularyPattern(String entity) {
        return """
            OPTIONAL { #entity ?isFromVocabulary ?vocabulary . }
            OPTIONAL {
                FILTER(!BOUND(?vocabulary)) .
                #entity ?hasVocabulary ?vocabulary .
            }
            OPTIONAL {
                FILTER(!BOUND(?vocabulary)) .
                #entity ?inDocument ?entityDocument .
                ?entityDocument ?hasVocabulary ?vocabulary .
            }
            OPTIONAL {
                FILTER(!BOUND(?vocabulary)) .
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
     *
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
                    FILTER(BOUND(?vocabulary)) .
                    ?vocabulary ?hasLanguage ?vocabularyLanguage .
                }
                BIND (COALESCE(?entityLanguage, ?vocabularyLanguage, ?language) as ?language) .
                """.replace("#entity", entity);
    }

    /**
     * Builds a nested series of replace() calls to normalize and order characters in a sentence.
     * <p>
     * First, converts the input variable to lowercase, then replaces Czech
     * accented characters with placeholder sequences to ensure proper alphabetical sorting.
     *
     * @param var the name of the variable (including the question mark)
     *            or expression to transform (will be lowercased and have accents replaced)
     * @return a nested SQL replace() expression string that lowercases the input and substitutes
     * accented characters with sorting-friendly sequences
     */
    public static String orderSentence(String var) {
        return r(r(r(r(r(r(r(r(r(r(r(r(r(r("lcase(" + var + ")",
            "'á'", "'azz'"),
            "'č'", "'czz'"),
            "'ď'", "'dzz'"),
            "'é'", "'ezz'"),
            "'ě'", "'ezz'"),
            "'í'", "'izz'"),
            "'ň'", "'nzz'"),
            "'ó'", "'ozz'"),
            "'ř'", "'rzz'"),
            "'š'", "'szz'"),
            "'ť'", "'tzz'"),
            "'ú'", "'uzz'"),
            "'ý'", "'yzz'"),
            "'ž'", "'zzz'");
    }

    /**
     * Constructs a SPARQL replace function call.
     *
     * @param haystack    the value in which to search
     * @param needle      the substring to find and replace
     * @param replacement the string to substitute in place of {@code needle}
     * @return a SPARQL replace function call in the form {@code replace(haystack, needle, replacement)}
     */
    private static String r(String haystack, String needle, String replacement) {
        return "replace(" + haystack + ", " + needle + ", " + replacement + ")";
    }

    private SparqlPatterns() {
        throw new AssertionError();
    }
}

/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Repository
public class TermAssignmentDao extends BaseDao<TermAssignment> {

    private static final String OCCURRENCES_CONDITION = "{" +
            "  ?x a ?suggestedOccurrence ." +
            "  BIND (true as ?suggested)" +
            "} UNION {" +
            "  ?x a ?occurrence ." +
            "  FILTER NOT EXISTS {" +
            "    ?x a ?suggestedOccurrence ." +
            "  }" +
            "  BIND (false as ?suggested)" +
            "} " +
            "  ?x ?hasTerm ?term ;" +
            "     ?hasTarget ?target . " +
            "  { ?target ?hasSource ?resource . FILTER NOT EXISTS { ?resource a ?fileType . } } " +
            "  UNION { ?target ?hasSource ?file . ?resource ?isDocumentOf ?file . } " +
            "BIND (IF(EXISTS { ?resource a ?termType }, ?termDefOcc, ?fileOcc) as ?type)";

    private final Persistence config;

    @Autowired
    public TermAssignmentDao(EntityManager em, Configuration config) {
        super(TermAssignment.class, em);
        this.config = config.getPersistence();
    }

    /**
     * Gets information about assignments and occurrences of the specified {@link Term}.
     *
     * @param term Term whose assignments and occurrences to retrieve
     * @return List of {@code TermAssignments} and {@code TermOccurrences}
     */
    public List<TermAssignments> getAssignmentInfo(Term term) {
        Objects.requireNonNull(term);
        return getOccurrences(term);
    }

    private List<TermAssignments> getOccurrences(Term term) {
        return em.createNativeQuery("SELECT ?term ?resource ?label (count(?x) as ?cnt) ?type ?suggested WHERE {" +
                                 "BIND (?t AS ?term)" +
                                 OCCURRENCES_CONDITION +
                                 "{ ?resource rdfs:label ?label . } UNION { ?resource ?hasTitle ?label . } " +
                                 "FILTER langMatches(lang(?label), ?lang)" +
                                 "} GROUP BY ?resource ?term ?label ?type ?suggested HAVING (?cnt > 0) ORDER BY ?label",
                         "TermOccurrences")
                 .setParameter("suggestedOccurrence", URI.create(Vocabulary.s_c_navrzeny_vyskyt_termu))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                 .setParameter("occurrence", URI.create(Vocabulary.s_c_vyskyt_termu))
                 .setParameter("hasTitle", URI.create(DC.Terms.TITLE))
                 .setParameter("isDocumentOf", URI.create(Vocabulary.s_p_ma_soubor))
                 .setParameter("fileType", URI.create(Vocabulary.s_c_soubor))
                 .setParameter("lang", config.getLanguage())
                 .setParameter("termType", URI.create(Vocabulary.s_c_term))
                 .setParameter("termDefOcc", URI.create(Vocabulary.s_c_definicni_vyskyt_termu))
                 .setParameter("fileOcc", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                 .setParameter("t", term.getUri()).getResultList();
    }

    public List<URI> getUnusedTermsInVocabulary(cz.cvut.kbss.termit.model.Vocabulary vocabulary) {
        return em.createNativeQuery("SELECT DISTINCT ?term WHERE { "
                                 + " ?term ?inVocabulary ?vocabulary . "
                                 + " FILTER NOT EXISTS {?x ?hasTerm ?term ; "
                                 + " ?hasTarget/?hasSource ?resource.}"
                                 + "}",
                         URI.class)
                 .setParameter("vocabulary", vocabulary.getUri())
                 .setParameter("inVocabulary",
                         URI.create(
                                 cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj)).getResultList();
    }
}

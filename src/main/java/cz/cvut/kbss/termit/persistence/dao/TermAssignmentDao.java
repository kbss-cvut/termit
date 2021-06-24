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
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Repository
public class TermAssignmentDao extends BaseDao<TermAssignment> {

    private static final String ASSIGNMENTS_CONDITION = "{" +
            "  ?x a ?suggestedAssignment ." +
            "  BIND (true as ?suggested)" +
            "  } UNION {" +
            "  ?x a ?assignment ." +
            "  FILTER NOT EXISTS {" +
            "    ?x a ?type ." +
            "    ?type rdfs:subClassOf+ ?assignment ." +
            "    FILTER (?type != ?assignment) " +
            "  }" +
            "  BIND (false as ?suggested)" +
            "  }" +
            "  ?x ?hasTerm ?term ;" +
            "     ?hasTarget ?target . " +
            "  { ?target ?hasSource ?resource . FILTER NOT EXISTS { ?resource a ?fileType . } } " +
            "  UNION { ?target ?hasSource ?file . ?resource ?isDocumentOf ?file . }";

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
        final List<TermAssignments> lst = getAssignments(term);
        lst.addAll(getOccurrences(term));
        return lst;
    }

    private List<TermAssignments> getAssignments(Term term) {
        return em.createNativeQuery("SELECT DISTINCT ?term ?resource ?label ?suggested WHERE {" +
                "BIND (?t AS ?term)" +
                ASSIGNMENTS_CONDITION +
                "  ?resource ?hasLabel ?label ." +
                "FILTER langMatches(lang(?label), ?lang)" +
                "} ORDER BY ?label", "TermAssignments")
                 .setParameter("suggestedAssignment", URI.create(Vocabulary.s_c_navrzene_prirazeni_termu))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                 .setParameter("assignment", URI.create(Vocabulary.s_c_prirazeni_termu))
                 .setParameter("isDocumentOf", URI.create(Vocabulary.s_p_ma_soubor))
                 .setParameter("fileType", URI.create(Vocabulary.s_c_soubor))
                 .setParameter("hasLabel", URI.create(DC.Terms.TITLE))
                 .setParameter("lang", config.getLanguage())
                 .setParameter("t", term.getUri()).getResultList();
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

    public List<TermAssignment> findByTarget(Target target) {
        Objects.requireNonNull(target);
        return em.createNativeQuery("SELECT ?x WHERE { ?x a ?type ; ?hasTarget ?target. }",
                TermAssignment.class).setParameter("type", typeUri)
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("target", target.getUri()).getResultList();
    }

    /**
     * Finds all assignments whose target represents this resource.
     * <p>
     * This includes both term assignments and term occurrences.
     *
     * @param resource Target resource to filter by
     * @return List of matching assignments
     */
    public List<TermAssignment> findAll(Resource resource) {
        Objects.requireNonNull(resource);
        return em.createNativeQuery("SELECT ?x WHERE { ?x a ?type ; ?hasTarget/?hasSource ?resource. }",
                TermAssignment.class).setParameter("type", typeUri)
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                 .setParameter("resource", resource.getUri()).getResultList();
    }

    /**
     * Gets information about term occurrences and assignments for the specified resource.
     *
     * @param resource Resource for which Term occurrences and assignments info should be retrieved
     * @return List of {@code ResourceTermAssignments} and {@code ResourceTermOccurrences}
     */
    public List<ResourceTermAssignments> getAssignmentInfo(Resource resource) {
        final List<ResourceTermAssignments> assignments = getAssignments(resource);
        final List<ResourceTermAssignments> occurrences = getOccurrences(resource);
        assignments.addAll(occurrences);
        return assignments;
    }

    private List<ResourceTermAssignments> getAssignments(Resource resource) {
        return em.createNativeQuery("SELECT DISTINCT ?term ?label ?vocabulary ?res ?suggested WHERE {" +
                ASSIGNMENTS_CONDITION +
                "  ?term ?hasLabel ?label ;" +
                "  ?inVocabulary ?vocabulary ." +
                "FILTER langMatches(lang(?label), ?lang)" +
                "BIND (?resource AS ?res)" +
                "} ORDER BY ?label", "ResourceTermAssignments")
                 .setParameter("suggestedAssignment", URI.create(Vocabulary.s_c_navrzene_prirazeni_termu))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                 .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("isDocumentOf", URI.create(Vocabulary.s_p_ma_soubor))
                 .setParameter("fileType", URI.create(Vocabulary.s_c_soubor))
                 .setParameter("assignment", URI.create(Vocabulary.s_c_prirazeni_termu))
                 .setParameter("lang", config.getLanguage())
                 .setParameter("resource", resource.getUri()).getResultList();
    }

    private List<ResourceTermAssignments> getOccurrences(Resource resource) {
        return em.createNativeQuery("SELECT ?term ?label ?vocabulary (count(?x) as ?cnt) ?res ?suggested WHERE {" +
                        OCCURRENCES_CONDITION +
                        "  ?term ?hasLabel ?label ;" +
                        "    ?inVocabulary ?vocabulary ." +
                        "FILTER langMatches(lang(?label), ?lang)" +
                        "BIND (?resource AS ?res)" +
                        "} GROUP BY ?term ?label ?vocabulary ?res ?suggested HAVING (?cnt > 0) ORDER BY ?label",
                "ResourceTermOccurrences")
                 .setParameter("suggestedOccurrence", URI.create(Vocabulary.s_c_navrzeny_vyskyt_termu))
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                 .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                 .setParameter("isDocumentOf", URI.create(Vocabulary.s_p_ma_soubor))
                 .setParameter("fileType", URI.create(Vocabulary.s_c_soubor))
                 .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("occurrence", URI.create(Vocabulary.s_c_vyskyt_termu))
                 .setParameter("lang", config.getLanguage())
                 .setParameter("resource", resource.getUri()).getResultList();
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

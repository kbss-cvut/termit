/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.util.SparqlResultToTermOccurrenceMapper;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Repository
public class TermOccurrenceDao extends BaseDao<TermOccurrence> {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceDao.class);

    /**
     * Perf #1283
     * <p>
     * Query for loading term occurrences targeting a specified source (file, another term) in a single go.
     */
    private static final String FIND_ALL_TARGETING_QUERY =
            "SELECT ?occ ?type ?term ?target ?suggested ?selector ?exactMatch ?prefix ?suffix ?startPosition ?endPosition WHERE {" +
                    "?occ a ?occurrence ." +
                    "GRAPH ?g { " +
                    "?occ a ?type ;" +
                    "   ?hasTarget ?target ." +
                    "OPTIONAL {" +
                    "   ?occ ?assignmentOfTerm ?term ." +
                    "} }" +
                    "?target a ?occurrenceTarget ;" +
                    "   ?hasSource ?source ." +
                    "OPTIONAL {" +
                    "   ?target ?hasSelector ?selector ." +
                    "   ?selector a ?selectorType ." +
                    "   {" +
                    "       ?selector ?hasExactMatch ?exactMatch ." +
                    "       OPTIONAL { ?selector ?hasPrefix ?prefix . }" +
                    "       OPTIONAL { ?selector ?hasSuffix ?suffix . }" +
                    "   } UNION {" +
                    "       ?selector ?hasStart ?startPosition ;" +
                    "           ?hasEnd ?endPosition ." +
                    "   } " +
                    "} " +
                    "FILTER (?type = ?fileOccurrence || ?type = ?definitionalOccurrence)" +
                    "BIND(EXISTS { ?occ a ?suggestedType . } as ?suggested)" +
                    "} GROUP BY ?occ ?type ?term ?target ?suggested ?selector ?exactMatch ?prefix ?suffix ?startPosition ?endPosition";

    private final Configuration.Persistence config;

    public TermOccurrenceDao(EntityManager em, Configuration config) {
        super(TermOccurrence.class, em);
        this.config = config.getPersistence();
    }

    /**
     * Finds all occurrences of the specified term.
     *
     * @param term Term whose occurrences should be returned
     * @return List of term occurrences
     */
    public List<TermOccurrence> findAllOf(AbstractTerm term) {
        Objects.requireNonNull(term);
        return em.createNativeQuery("SELECT ?x WHERE {" +
                                            "?x a ?type ;" +
                                            "?hasTerm ?term . }", TermOccurrence.class)
                 .setParameter("type", typeUri)
                 .setParameter("hasTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("term", term.getUri()).getResultList();
    }

    /**
     * Finds all definitional occurrences of the specified term.
     *
     * @param term Term whose occurrences should be returned
     * @return List of term occurrences
     */
    public List<TermOccurrence> findAllDefinitionalOf(AbstractTerm term) {
        Objects.requireNonNull(term);
        return em
                .createQuery("SELECT to FROM TermDefinitionalOccurrence to WHERE to.term = :term", TermOccurrence.class)
                .setParameter("term", term).getResultList();
    }

    /**
     * Finds all term occurrences whose target points to the specified resource.
     * <p>
     * I.e., these term occurrences appear in the specified resource (presumably file).
     *
     * @param target Asset to filter by
     * @return List of matching term occurrences
     */
    public List<TermOccurrence> findAllTargeting(Asset<?> target) {
        Objects.requireNonNull(target);
        final Query query = em.createNativeQuery(FIND_ALL_TARGETING_QUERY)
                              .setParameter("g", TermOccurrence.resolveContext(target.getUri()))
                              .setParameter("occurrence", URI.create(Vocabulary.s_c_vyskyt_termu))
                              .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
                              .setParameter("assignmentOfTerm", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                              .setParameter("occurrenceTarget", URI.create(Vocabulary.s_c_cil_vyskytu))
                              .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
                              .setParameter("source", target.getUri())
                              .setParameter("hasSelector", URI.create(Vocabulary.s_p_ma_selektor))
                              .setParameter("selectorType", URI.create(Vocabulary.s_c_selektor))
                              .setParameter("hasExactMatch", URI.create(Vocabulary.s_p_ma_presny_text_quote))
                              .setParameter("hasPrefix", URI.create(Vocabulary.s_p_ma_prefix_text_quote))
                              .setParameter("hasSuffix", URI.create(Vocabulary.s_p_ma_suffix_text_quote))
                              .setParameter("hasStart", URI.create(Vocabulary.s_p_ma_startovni_pozici))
                              .setParameter("hasEnd", URI.create(Vocabulary.s_p_ma_koncovou_pozici))
                              .setParameter("fileOccurrence", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                              .setParameter("definitionalOccurrence", URI.create(Vocabulary.s_c_definicni_vyskyt_termu))
                              .setParameter("suggestedType", URI.create(Vocabulary.s_c_navrzeny_vyskyt_termu));
        return new SparqlResultToTermOccurrenceMapper(target.getUri()).map(query.getResultList());
    }

    /**
     * Gets aggregated information about occurrences of the specified {@link Term}.
     *
     * @param term Term whose occurrences to retrieve
     * @return List of {@code TermOccurrences}
     */
    public List<TermOccurrences> getOccurrenceInfo(AbstractTerm term) {
        return em.createNativeQuery("SELECT ?term ?resource ?label (count(?x) as ?cnt) ?type ?suggested WHERE {" +
                                            "BIND (?t AS ?term)" +
                                            "{" +
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
                                            "BIND (IF(EXISTS { ?resource a ?termType }, ?termDefOcc, ?fileOcc) as ?type)" +
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
                 .setParameter("termType", URI.create(SKOS.CONCEPT))
                 .setParameter("termDefOcc", URI.create(Vocabulary.s_c_definicni_vyskyt_termu))
                 .setParameter("fileOcc", URI.create(Vocabulary.s_c_souborovy_vyskyt_termu))
                 .setParameter("t", term.getUri()).getResultList();
    }

    @Override
    public void persist(TermOccurrence entity) {
        Objects.requireNonNull(entity);
        try {
            final Descriptor descriptor = new EntityDescriptor(entity.resolveContext());
            em.persist(entity, descriptor);
            // Ensure target is saved as well
            if (entity.getTarget().getUri() == null) {
                em.persist(entity.getTarget(), descriptor);
            }
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public TermOccurrence update(TermOccurrence entity) {
        Objects.requireNonNull(entity);
        try {
            // Evict possibly cached references with default context (cached by this.find)
            em.getEntityManagerFactory().getCache().evict(TermOccurrence.class, entity.getUri(), null);
            return em.merge(entity, new EntityDescriptor(entity.resolveContext()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Removes all suggested term occurrences whose target points to the specified asset.
     *
     * @param target Asset for which suggested term occurrences will be removed
     */
    public void removeSuggested(Asset<?> target) {
        Objects.requireNonNull(target);
        removeAll(target.getUri(), URI.create(Vocabulary.s_c_navrzeny_vyskyt_termu));
    }

    private void removeAll(URI assetUri, URI toType) {
        em.createNativeQuery("DELETE WHERE {" +
                                     "?x a ?toType ;" +
                                     "?hasTarget ?target ;" +
                                     "?y ?z ." +
                                     "?target a ?occurrenceTarget ;" +
                                     "?hasSelector ?selector ;" +
                                     "?hasSource ?asset ." +
                                     "?target ?tY ?tZ ." +
                                     "?selector ?sY ?sZ . }")
          .setParameter("toType", toType)
          .setParameter("hasTarget", URI.create(Vocabulary.s_p_ma_cil))
          .setParameter("occurrenceTarget", URI.create(Vocabulary.s_c_cil_vyskytu))
          .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
          .setParameter("asset", assetUri)
          .setParameter("hasSelector", URI.create(Vocabulary.s_p_ma_selektor)).executeUpdate();
    }

    /**
     * Removes all term occurrences whose target points to the specified asset.
     * <p>
     * Note that the removal may not be immediate. Rather, the occurrences are moved into a context that is scheduled
     * for removal later.
     *
     * @param target Asset for which term occurrences will be removed
     */
    public void removeAll(Asset<?> target) {
        Objects.requireNonNull(target);

        final URI sourceContext = TermOccurrence.resolveContext(target.getUri());
        LOG.debug("Removing all occurrences from {}", Utils.uriToString(sourceContext));
        em.createNativeQuery("DROP GRAPH ?context")
          .setParameter("context", sourceContext)
          .executeUpdate();
        LOG.atDebug().setMessage("Removed all occurrences from {}")
           .addArgument(() -> Utils.uriToString(sourceContext)).log();
    }

    /**
     * Removes all occurrences of the specified term.
     * <p>
     * That is, remove all term occurrences whose subject (not target) is the specified term.
     *
     * @param term Term whose occurrences to remove
     */
    public void removeAllOf(AbstractTerm term) {
        findAllOf(term).forEach(em::remove);
    }

    /**
     * Removes all term occurrence whose target points to a non-existent asset.
     * <p>
     * This method exists mainly for legacy reasons - since occurrences are now stored in a particular context, their
     * batch removal (e.g., on corresponding asset remove) is implemented by dropping the whole context. However, old
     * occurrences were stored in the default context and thus the new removal logic does not affect them. This method
     * allows targeting such occurrences.
     */
    public void removeAllOrphans() {
        em.createNativeQuery("SELECT DISTINCT ?source WHERE {" +
                                     "?t a ?target ;" +
                                     "?hasSource ?source ." +
                                     // If an asset does not have a label, it does not exist
                                     "FILTER NOT EXISTS { " +
                                     "{ ?source ?hasLabel ?label . } " +
                                     "UNION" +
                                     "{ ?source ?hasTitle ?label . } " +
                                     "}}", URI.class)
          .setParameter("target", URI.create(Vocabulary.s_c_cil_vyskytu))
          .setParameter("hasSource", URI.create(Vocabulary.s_p_ma_zdroj))
          .setParameter("hasLabel", URI.create(RDFS.LABEL))
          .setParameter("hasTitle", URI.create(DC.Terms.TITLE))
          .getResultStream().forEach(a -> {
              LOG.trace("Removing orphaned term occurrences targeting <{}>.", a);
              removeAll(a, URI.create(Vocabulary.s_c_vyskyt_termu));
          });
    }
}

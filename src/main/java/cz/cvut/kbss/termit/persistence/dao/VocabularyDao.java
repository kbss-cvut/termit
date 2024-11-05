/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.RdfsStatement;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.filter.VocabularyContentChangeFilterDto;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.event.BeforeAssetDeleteEvent;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyWillBeRemovedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.snapshot.AssetSnapshotLoader;
import cz.cvut.kbss.termit.persistence.validation.VocabularyContentValidator;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.throttle.CacheableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.util.Constants.DEFAULT_PAGE_SIZE;
import static cz.cvut.kbss.termit.util.Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS;

@Repository
public class VocabularyDao extends BaseAssetDao<Vocabulary>
        implements SnapshotProvider<Vocabulary>, SupportsLastModification {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyDao.class);

    private static final URI LABEL_PROPERTY = URI.create(DC.Terms.TITLE);
    private static final String CONTENT_CHANGES_QUERY = "SELECT ?date (COUNT(DISTINCT(?t)) as ?cnt) WHERE { " +
            "    ?ch a ?type ; " +
            "        ?hasEntity ?t ; " +
            "        ?hasTimestamp ?timestamp . " +
            "    ?t ?inVocabulary ?vocabulary . " +
            "    BIND (SUBSTR(STR(?timestamp), 1, 10) as ?date) " +
            "} GROUP BY ?date HAVING (?cnt > 0) ORDER BY ?date";

    private static final String REMOVE_GLOSSARY_TERMS_QUERY_FILE = "remove/removeGlossaryTerms.ru";
    private final ChangeTrackingContextResolver changeTrackingContextResolver;

    private volatile long lastModified;

    private final VocabularyContextMapper contextMapper;

    private final ApplicationContext context;

    @Autowired
    public VocabularyDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory,
                         VocabularyContextMapper contextMapper, ApplicationContext context,
                         ChangeTrackingContextResolver changeTrackingContextResolver) {
        super(Vocabulary.class, em, config.getPersistence(), descriptorFactory);
        this.contextMapper = contextMapper;
        refreshLastModified();
        this.context = context;
        this.changeTrackingContextResolver = changeTrackingContextResolver;
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROPERTY;
    }

    @Override
    public List<Vocabulary> findAll() {
        try {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?v a ?type ;" +
                                                "?hasTitle ?title ." +
                                                "FILTER NOT EXISTS {" +
                                                "?v a ?snapshot ." +
                                                "}} ORDER BY ?title", type)
                     .setParameter("type", typeUri)
                     .setParameter("hasTitle", URI.create(DC.Terms.TITLE))
                     .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku))
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Optional<Vocabulary> find(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.find(type, id, descriptorFactory.vocabularyDescriptor(id)));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Vocabulary getReference(URI id) {
        Objects.requireNonNull(id);
        try {
            return em.getReference(type, id, descriptorFactory.vocabularyDescriptor(id));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of all vocabularies imported by the specified vocabulary, including transitively imported ones.
     *
     * @param vocabularyIri Identifier of base vocabulary, whose imports should be retrieved
     * @return Collection of (transitively) imported vocabularies
     */
    public Collection<URI> getTransitivelyImportedVocabularies(URI vocabularyIri) {
        Objects.requireNonNull(vocabularyIri);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?imported WHERE {" +
                                                "?x ?imports+ ?imported ." +
                                                "}", URI.class)
                     .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                     .setParameter("x", vocabularyIri).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of vocabularies which directly import the supplied one.
     *
     * @param vocabulary vocabulary, importing vocabularies of which are fetched
     * @return Collection of vocabularies which directly import #vocabulary
     */
    public List<Vocabulary> getImportingVocabularies(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?importing WHERE {" +
                                                "?importing ?imports ?imported ." +
                                                "}", Vocabulary.class)
                     .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                     .setParameter("imported", vocabulary.getUri()).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void persist(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            em.persist(entity, descriptorFactory.vocabularyDescriptor(entity));
            if (entity.getDocument() != null && em.find(Document.class, entity.getDocument().getUri()) == null) {
                em.persist(entity.getDocument(), descriptorFactory.documentDescriptor(entity));
            }
            refreshLastModified();
            eventPublisher.publishEvent(new AssetPersistEvent(this, entity));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public Vocabulary update(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            eventPublisher.publishEvent(new AssetUpdateEvent(this, entity));
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Vocabulary.class, entity.getUri(), null);
            final Vocabulary result = em.merge(entity, descriptorFactory.vocabularyDescriptor(entity));
            refreshLastModified();
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Forcefully removes the specified vocabulary.
     * <p>
     * This deletes the whole graph of the vocabulary, all terms in the vocabulary's glossary and then removes the vocabulary itself. Extreme caution
     * should be exercised when using this method. All relevant data, including documents and files, will be dropped.
     * <p>
     * Publishes {@link VocabularyWillBeRemovedEvent} before the actual removal to allow other services to clean up related resources (e.g., delete the document).
     * @param entity The vocabulary to delete
     */
    @ModifiesData
    @Override
    public void remove(Vocabulary entity) {
        eventPublisher.publishEvent(new VocabularyWillBeRemovedEvent(this, entity.getUri()));
        eventPublisher.publishEvent(new BeforeAssetDeleteEvent(this, entity));
        this.removeVocabulary(entity, true);
    }

    /**
     * Does not publish the {@link VocabularyWillBeRemovedEvent}.
     * <p>
     * Forcefully removes the specified vocabulary.
     * <p>
     * This deletes all terms in the vocabulary's glossary and then removes the vocabulary itself.
     * Extreme caution should be exercised when using this method,
     * as it does not check for any references or usage and just drops all the relevant data.
     * <p>
     * The document is not removed.
     */
    public void removeVocabularyKeepDocument(Vocabulary entity) {
        this.removeVocabulary(entity, false);
    }

    /**
     * <p>
     * Does not publish the {@link VocabularyWillBeRemovedEvent}.<br>
     * You should use {@link #remove(Vocabulary)} instead.
     * <p>
     * Forcefully removes the specified vocabulary.
     * <p>
     * This deletes all terms in the vocabulary's glossary and then removes the vocabulary itself. Extreme caution
     * should be exercised when using this method, as it does not check for any references or usage and just drops all
     * the relevant data.
     * @param entity The vocabulary to delete
     * @param dropGraph if false,
     *                  executes {@code  src/main/resources/query/remove/removeGlossaryTerms.ru} removing terms,
     *                  their relations, model, glossary and vocabulary itself, keeps the document.
     *                  When true, the whole vocabulary graph is dropped.
     */
    private void removeVocabulary(Vocabulary entity, boolean dropGraph) {
        Objects.requireNonNull(entity);
        LOG.debug("Forcefully removing vocabulary {} and all its contents.", entity);
        try {
            final URI vocabularyContext = contextMapper.getVocabularyContext(entity.getUri());

            if(dropGraph) {
                // drops whole named graph
                em.createNativeQuery("DROP GRAPH ?context")
                  .setParameter("context", vocabularyContext)
                  .executeUpdate();
            } else {
                // removes all terms and their relations from named graph
                em.createNativeQuery(Utils.loadQuery(REMOVE_GLOSSARY_TERMS_QUERY_FILE))
                  .setParameter("g", vocabularyContext)
                  .setParameter("vocabulary", entity.getUri())
                  .executeUpdate();
            }

            find(entity.getUri()).ifPresent(em::remove);
            refreshLastModified();
            em.getEntityManagerFactory().getCache().evict(vocabularyContext);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Updates glossary contained in the specified vocabulary.
     * <p>
     * The vocabulary is passed for correct context resolution, as glossary existentially depends on its owning
     * vocabulary.
     *
     * @param entity Owner of the updated glossary
     * @return The updated entity
     */
    public Glossary updateGlossary(Vocabulary entity) {
        Objects.requireNonNull(entity);
        final Glossary result = em.merge(entity.getGlossary(), descriptorFactory.glossaryDescriptor(entity));
        refreshLastModified();
        return result;
    }

    /**
     * Finds a glossary given its URI.
     *
     * @param uri glossary URI to find
     * @return Glossary, if found
     */
    public Optional<Glossary> findGlossary(URI uri) {
        Objects.requireNonNull(uri);
        return Optional.ofNullable(em.find(Glossary.class, uri));
    }

    /**
     * Checks whether terms from the {@code subjectVocabulary} reference (as parent terms) any terms from the {@code
     * targetVocabulary}.
     *
     * @param subjectVocabulary Subject vocabulary identifier
     * @param targetVocabulary  Target vocabulary identifier
     * @return Whether subject vocabulary terms reference target vocabulary terms
     */
    public boolean hasInterVocabularyTermRelationships(URI subjectVocabulary, URI targetVocabulary) {
        Objects.requireNonNull(subjectVocabulary);
        Objects.requireNonNull(targetVocabulary);
        return em.createNativeQuery("ASK WHERE {" +
                                            "    ?t ?isTermFromVocabulary ?subjectVocabulary ; " +
                                            "       ?hasParentTerm ?parent . " +
                                            "    ?parent ?isTermFromVocabulary ?import . " +
                                            "    {" +
                                            "        SELECT ?import WHERE {" +
                                            "           ?targetVocabulary ?importsVocabulary* ?import . " +
                                            "} } }", Boolean.class)
                 .setParameter("isTermFromVocabulary",
                               URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("subjectVocabulary", subjectVocabulary)
                 .setParameter("hasParentTerm", URI.create(SKOS.BROADER))
                 .setParameter("targetVocabulary", targetVocabulary)
                 .setParameter("importsVocabulary",
                               URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                 .getSingleResult();
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public void refreshLastModified() {
        this.lastModified = System.currentTimeMillis();
    }

    @EventListener
    public void refreshLastModified(RefreshLastModifiedEvent event) {
        refreshLastModified();
    }

    @Transactional
    public CacheableFuture<Collection<ValidationResult>> validateContents(URI vocabulary) {
        final VocabularyContentValidator validator = context.getBean(VocabularyContentValidator.class);
        final Collection<URI> importClosure = getTransitivelyImportedVocabularies(vocabulary);
        importClosure.add(vocabulary);
        return validator.validate(vocabulary, importClosure);
    }

    /**
     * Gets aggregated information about changes to the terms in the specified vocabulary.
     *
     * @param vocabulary Vocabulary to get changes for
     * @return List of aggregated change information objects
     */
    public List<AggregatedChangeInfo> getChangesOfContent(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        final List<AggregatedChangeInfo> persists = createContentChangesQuery(vocabulary)
                .setParameter("type", URI.create(
                        cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity)).getResultList();
        persists.forEach(p -> p.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity));
        final List<AggregatedChangeInfo> updates = createContentChangesQuery(vocabulary)
                .setParameter("type", URI.create(
                        cz.cvut.kbss.termit.util.Vocabulary.s_c_uprava_entity)).getResultList();
        updates.forEach(u -> u.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_uprava_entity));
        final List<AggregatedChangeInfo> deletitions =  createContentChangesQuery(vocabulary)
                .setParameter("type", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_smazani_entity)).getResultList();
        deletitions.forEach(d -> d.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_smazani_entity));
        return Stream.of(persists, updates, deletitions)
                     .flatMap(List::stream)
                     .sorted()
                     .toList();
    }

    /**
     * Gets content change records of the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose content changes to get
     * @param pageReq Specification of the size and number of the page to return
     * @return List of change records, ordered by date in descending order
     */
    public List<AbstractChangeRecord> getDetailedHistoryOfContent(Vocabulary vocabulary, VocabularyContentChangeFilterDto filter, Pageable pageReq) {
        Objects.requireNonNull(vocabulary);
        return createDetailedContentChangesQuery(vocabulary, filter, pageReq).getResultList();
    }

    private TypedQuery<AbstractChangeRecord> createDetailedContentChangesQuery(Vocabulary vocabulary, VocabularyContentChangeFilterDto filter, Pageable pageReq) {
        TypedQuery<AbstractChangeRecord> query = em.createNativeQuery("""
                         SELECT DISTINCT ?record WHERE {
""" + /* Select anything from change context */ """
                            GRAPH ?changeContext {
                                ?record a ?changeRecord .
                            }
""" + /* The record should be a subclass of "zmena" */ """
                            ?changeRecord ?subClassOf+ ?zmena .
                            ?record ?relatesTo ?term ;
                                ?hasTime ?timestamp ;
                                ?hasAuthor ?author .
""" + /* Get author's name */ """
                            ?author ?hasFirstName ?firstName ;
                                ?hasLastName ?lastName .
                            BIND(CONCAT(?firstName, " ", ?lastName) as ?authorFullName)
""" + /* When its update record, there will be a changed attribute */ """
                            OPTIONAL {
                               ?record ?hasChangedAttribute ?attribute .
                               ?attribute ?hasRdfsLabel ?changedAttributeName .
                            }
""" + /* Get term's name (but the term might have been already deleted) */ """
                            OPTIONAL {
                               ?term a ?termType ;
                                   ?hasLabel ?label .
                            }
""" + /* then try to get the label from (delete) record */ """
                            OPTIONAL {
                               ?record ?hasRdfsLabel ?label .
                            }
""" + /* When label is still not bound, the term was probably deleted, find the delete record and get the label from it */ """
                            OPTIONAL {
                                FILTER(!BOUND(?label)) .
                                ?deleteRecord a <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/smazání-entity>;
                                <http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/má-změněnou-entitu> ?term;
                                <http://www.w3.org/2000/01/rdf-schema#label> ?label.
                            }
                            BIND(?termName as ?termNameVal)
                            BIND(?authorName as ?authorNameVal)
                            BIND(?attributeName as ?changedAttributeNameVal)
                            FILTER (!BOUND(?termNameVal) || CONTAINS(LCASE(?label), LCASE(?termNameVal)))
                            FILTER (!BOUND(?authorNameVal) || CONTAINS(LCASE(?authorFullName), LCASE(?authorNameVal)))
                            FILTER (!BOUND(?changedAttributeName) || !BOUND(?changedAttributeNameVal) || CONTAINS(LCASE(?changedAttributeName), LCASE(?changedAttributeName)))
                         } ORDER BY DESC(?timestamp) ?attribute
                         """, AbstractChangeRecord.class)
                       .setParameter("changeContext", changeTrackingContextResolver.resolveChangeTrackingContext(vocabulary))
                       .setParameter("subClassOf", URI.create(RDFS.SUB_CLASS_OF))
                       .setParameter("zmena", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_zmena))
                       .setParameter("termType", URI.create(SKOS.CONCEPT))
                       .setParameter("relatesTo", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zmenenou_entitu))
                       .setParameter("hasTime", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_modifikace))
                       .setParameter("hasChangedAttribute", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zmeneny_atribut))
                       .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL)) // term label
                       .setParameter("hasAuthor", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_editora)) // record has author
                       .setParameter("hasFirstName", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_krestni_jmeno))
                       .setParameter("hasLastName", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_prijmeni))
                       .setParameter("hasRdfsLabel", URI.create(RDFS.LABEL)); // changed attribute label

        if(!Utils.isBlank(filter.getTermName())) {
            query = query.setParameter("termName", filter.getTermName().trim());
        }
        if (!Utils.isBlank(filter.getAuthorName())) {
            query = query.setParameter("authorName", filter.getAuthorName().trim());
        }
        if (filter.getChangeType() != null) {
            query = query.setParameter("changeRecord", filter.getChangeType());
        }
        if (!Utils.isBlank(filter.getChangedAttributeName())) {
            query = query.setParameter("attributeName", filter.getChangedAttributeName().trim());
        }

        if(pageReq.isUnpaged()) {
            return query;
        }

        return query.setFirstResult((int) pageReq.getOffset())
                 .setMaxResults(pageReq.getPageSize());
    }

    private Query createContentChangesQuery(Vocabulary vocabulary) {
        return em.createNativeQuery(CONTENT_CHANGES_QUERY, "AggregatedChangeInfo")
                 .setParameter("hasEntity",
                               URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zmenenou_entitu))
                 .setParameter("hasTimestamp", URI.create(
                         cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_modifikace))
                 .setParameter("inVocabulary",
                               URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("vocabulary", vocabulary);
    }

    /**
     * Returns the number of all terms in the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose terms should be counted
     * @return Number of terms in a vocabulary, 0 if the vocabulary is empty or does not exist.
     */
    public Integer getTermCount(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return em.createQuery("SELECT DISTINCT COUNT(t) FROM Term t WHERE t.vocabulary = :vocabulary", Integer.class)
                 .setParameter("vocabulary", vocabulary).getSingleResult();
    }

    /**
     * Returns true if the specified vocabulary does not contain any terms.
     *
     * @param vocabulary Vocabulary to check for existence of terms
     * @return true, if the vocabulary contains no terms, false otherwise
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return !em.createNativeQuery("ASK WHERE {" +
                                                 "?term a ?type ;" +
                                                 "?inVocabulary ?vocabulary ." +
                                                 " }", Boolean.class)
                      .setParameter("type", URI.create(SKOS.CONCEPT))
                      .setParameter("vocabulary", vocabulary.getUri())
                      .setParameter("inVocabulary",
                                    URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                      .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public List<Snapshot> findSnapshots(Vocabulary vocabulary) {
        return new AssetSnapshotLoader<Vocabulary>(em, typeUri, URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku)).findSnapshots(vocabulary);
    }

    @Override
    public Optional<Vocabulary> findVersionValidAt(Vocabulary vocabulary, Instant at) {
        return new AssetSnapshotLoader<Vocabulary>(em, typeUri, URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku))
                .findVersionValidAt(vocabulary, at);
    }

    /**
     * Gets a set of identifiers of vocabularies that are related to the specified root vocabulary.
     * <p>
     * The relatedness is given by either a vocabulary explicitly importing another vocabulary or by any of the terms it
     * contains being in one of the specified relationships with a term from another vocabulary.
     * <p>
     * This mapping is cascaded until no more related vocabulary are found.
     * <p>
     * Note that the result contains also the identifier of the specified vocabulary.
     *
     * @param rootVocabulary    Identifier of the vocabulary to start from
     * @param termRelationships Inter-term relationships to be taken into account
     * @return A set of related vocabulary identifiers
     */
    public Set<URI> getRelatedVocabularies(Vocabulary rootVocabulary, Collection<URI> termRelationships) {
        Objects.requireNonNull(rootVocabulary);
        Objects.requireNonNull(termRelationships);

        final List<URI> result = new ArrayList<>();
        result.add(rootVocabulary.getUri());
        // Using old-school iteration to prevent concurrent modification issues when adding items to list under iteration
        for (int i = 0; i < result.size(); i++) {
            final Set<URI> toAdd = new HashSet<>(em.createNativeQuery("""
                                                                              SELECT DISTINCT ?v WHERE {
                                                                                  ?t a ?term ;
                                                                                     ?inVocabulary ?vocabulary ;
                                                                                     ?y ?z .
                                                                                  ?z a ?term ;
                                                                                     ?inVocabulary ?v .
                                                                                  FILTER (?v != ?vocabulary)
                                                                                  FILTER (?y IN (?cascadingRelationships))
                                                                              }""", URI.class)
                                                   .setParameter("term", URI.create(SKOS.CONCEPT))
                                                   .setParameter("inVocabulary",
                                                                 URI.create(
                                                                         cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                   .setParameter("vocabulary", result.get(i))
                                                   .setParameter("cascadingRelationships", termRelationships)
                                                   .getResultList());
            // Explicitly imported vocabularies (it is likely they were already added due to term relationships, but just
            // to be sure)
            toAdd.addAll(em.createNativeQuery("SELECT DISTINCT ?imported WHERE { ?v ?imports ?imported . }", URI.class)
                           .setParameter("imports",
                                         URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                           .setParameter("v", result.get(i)).getResultList());
            result.forEach(toAdd::remove);
            result.addAll(toAdd);
        }
        return new HashSet<>(result);
    }

    /**
     * Resolves preferred namespace prefix and URI of a vocabulary with the specified identifier.
     * <p>
     * This method expects that the prefix and namespace are declared on the vocabulary itself, not on its glossary.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Prefix declaration, possibly containing {@code null} values
     */
    public PrefixDeclaration resolvePrefix(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        try {
            final List<?> result = em.createNativeQuery("SELECT ?prefix ?namespace WHERE { " +
                                                                "?vocabulary ?hasPrefix ?prefix ; " +
                                                                "?hasNamespace ?namespace . }")
                                     .setParameter("vocabulary", vocabularyUri)
                                     .setParameter("hasPrefix", URI.create(
                                             cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespacePrefix))
                                     .setParameter("hasNamespace", URI.create(
                                             cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri))
                                     .getResultList();
            if (result.isEmpty()) {
                return PrefixDeclaration.EMPTY_PREFIX;
            }
            assert result.get(0) instanceof Object[];
            return new PrefixDeclaration(((Object[]) result.get(0))[0].toString(),
                                         ((Object[]) result.get(0))[1].toString());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * @return all relations between specified vocabulary and all other vocabularies
     */
    public List<RdfsStatement> getVocabularyRelations(Vocabulary vocabulary, Collection<URI> excludedRelations) {
        Objects.requireNonNull(vocabulary);
        final URI vocabularyUri = vocabulary.getUri();

        try {
            return em.createNativeQuery("""
                             SELECT DISTINCT ?object ?relation ?subject {
                                 ?object a ?vocabularyType ;
                                    ?relation ?subject . 
                                 FILTER(?object != ?subject) .
                                 FILTER(?relation NOT IN (?excluded)) .
                             } ORDER BY ?object ?relation
                             """, "RDFStatement")
                     .setParameter("subject", vocabularyUri)
                    .setParameter("excluded", excludedRelations)
                     .setParameter("vocabularyType", URI.create(EntityToOwlClassMapper.getOwlClassForEntity(Vocabulary.class)))
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * @return all relations between terms in specified vocabulary and all terms from any other vocabulary
     */
    public List<RdfsStatement> getTermRelations(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        final URI vocabularyUri = vocabulary.getUri();
        final URI termType = URI.create(EntityToOwlClassMapper.getOwlClassForEntity(Term.class));
        final URI inVocabulary = URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku);

        try {
            return em.createNativeQuery("""
                             SELECT DISTINCT ?object ?relation ?subject WHERE {
                                     ?term a ?termType;
                                         ?inVocabulary ?vocabulary .

                                     {
                                        ?term ?relation ?secondTerm .
                                        ?secondTerm a ?termType;
                                            ?inVocabulary ?secondVocabulary .
                                            
                                        BIND(?term as ?object)
                                        BIND(?secondTerm as ?subject)
                                     } UNION {
                                        ?secondTerm ?relation ?term .
                                        ?secondTerm a ?termType;
                                            ?inVocabulary ?secondVocabulary .

                                        BIND(?secondTerm as ?object)
                                        BIND(?term as ?subject)
                                     }

                                     FILTER(?relation IN (?deniedRelations))
                                     FILTER(?object != ?subject)
                                     FILTER(?secondVocabulary != ?vocabulary)
                             } ORDER by ?object ?relation ?subject
                             """, "RDFStatement"
                     ).setMaxResults(DEFAULT_PAGE_SIZE)
                     .setParameter("termType", termType)
                     .setParameter("inVocabulary", inVocabulary)
                     .setParameter("vocabulary", vocabularyUri)
                     .setParameter("deniedRelations", SKOS_CONCEPT_MATCH_RELATIONSHIPS)
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}

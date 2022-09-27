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
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.snapshot.AssetSnapshotLoader;
import cz.cvut.kbss.termit.persistence.validation.VocabularyContentValidator;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class VocabularyDao extends BaseAssetDao<Vocabulary>
        implements SnapshotProvider<Vocabulary>, SupportsLastModification {

    private static final URI LABEL_PROPERTY = URI.create(DC.Terms.TITLE);
    private static final String CONTENT_CHANGES_QUERY = "SELECT ?date (COUNT(DISTINCT(?t)) as ?cnt) WHERE { " +
            "    ?ch a ?type ; " +
            "        ?hasEntity ?t ; " +
            "        ?hasTimestamp ?timestamp . " +
            "    ?t ?inVocabulary ?vocabulary . " +
            "    BIND (SUBSTR(STR(?timestamp), 1, 10) as ?date) " +
            "} GROUP BY ?date HAVING (?cnt > 0) ORDER BY ?date";

    private volatile long lastModified;

    private final ApplicationContext context;

    @Autowired
    public VocabularyDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory,
                         ApplicationContext context) {
        super(Vocabulary.class, em, config.getPersistence(), descriptorFactory);
        refreshLastModified();
        this.context = context;
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
                                                "}} ORDER BY ?title", URI.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasTitle", URI.create(DC.Terms.TITLE))
                     .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku))
                     .getResultStream().map(this::find).flatMap(Optional::stream).collect(Collectors.toList());
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
    public Optional<Vocabulary> getReference(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.getReference(type, id, descriptorFactory.vocabularyDescriptor(id)));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of all vocabularies imported by the specified vocabulary, including transitively imported ones.
     *
     * @param entity Base vocabulary, whose imports should be retrieved
     * @return Collection of (transitively) imported vocabularies
     */
    public Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?imported WHERE {" +
                                                "?x ?imports+ ?imported ." +
                                                "}", URI.class)
                     .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                     .setParameter("x", entity.getUri()).getResultList();
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
    public Vocabulary update(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Vocabulary.class, entity.getUri(), null);
            return em.merge(entity, descriptorFactory.vocabularyDescriptor(entity));
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
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void remove(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            find(entity.getUri()).ifPresent(em::remove);
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
        return em.merge(entity.getGlossary(), descriptorFactory.glossaryDescriptor(entity));
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
    public List<ValidationResult> validateContents(Vocabulary voc) {
        final VocabularyContentValidator validator = context.getBean(VocabularyContentValidator.class);
        final Collection<URI> importClosure = getTransitivelyImportedVocabularies(voc);
        importClosure.add(voc.getUri());
        return validator.validate(importClosure);
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
        final List<AggregatedChangeInfo> result = new ArrayList<>(persists.size() + updates.size());
        result.addAll(persists);
        result.addAll(updates);
        Collections.sort(result);
        return result;
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
                      .setParameter("vocabulary", vocabulary)
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
            final Set<URI> toAdd = new HashSet<>(em.createNativeQuery("SELECT DISTINCT ?v WHERE {\n" +
                                                                              "    ?t a ?term ;\n" +
                                                                              "       ?inVocabulary ?vocabulary ;\n" +
                                                                              "       ?y ?z .\n" +
                                                                              "    ?z a ?term ;\n" +
                                                                              "       ?inVocabulary ?v .\n" +
                                                                              "    FILTER (?v != ?vocabulary)\n" +
                                                                              "    FILTER (?y IN (?cascadingRelationships))\n" +
                                                                              "}", URI.class)
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
}

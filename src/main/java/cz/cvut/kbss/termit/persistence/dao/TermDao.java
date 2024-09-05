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

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.VocabularyContentModifiedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.util.Cache;
import cz.cvut.kbss.termit.persistence.snapshot.AssetSnapshotLoader;
import cz.cvut.kbss.termit.service.snapshot.SnapshotProvider;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class TermDao extends BaseAssetDao<Term> implements SnapshotProvider<Term> {

    private static final URI LABEL_PROP = URI.create(SKOS.PREF_LABEL);

    private final Cache<URI, Set<TermInfo>> subTermsCache;

    private final Comparator<TermInfo> termInfoComparator;

    private final VocabularyContextMapper contextMapper;

    @Autowired
    public TermDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory,
                   Cache<URI, Set<TermInfo>> subTermsCache, VocabularyContextMapper contextMapper) {
        super(Term.class, em, config.getPersistence(), descriptorFactory);
        this.subTermsCache = subTermsCache;
        this.termInfoComparator = Comparator.comparing(t -> t.getLabel().get(config.getPersistence().getLanguage()),
                                                       Comparator.nullsLast(Comparator.naturalOrder()));
        this.contextMapper = contextMapper;
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROP;
    }

    @Override
    public Optional<Term> find(URI id) {
        final Optional<Term> result = super.find(id);
        result.ifPresent(this::postLoad);
        return result;
    }

    private void postLoad(Term r) {
        r.setSubTerms(getSubTerms(r));
        r.setInverseRelated(loadInverseRelatedTerms(r));
        r.setInverseRelatedMatch(loadInverseRelatedMatchTerms(r));
        r.setInverseExactMatchTerms(loadInverseExactMatchTerms(r));
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetry of SKOS related.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseRelatedTerms(Term term) {
        return loadInverseTermInfo(term, SKOS.RELATED,
                                   Utils.joinCollections(term.getRelated(), term.getRelatedMatch()));
    }

    /**
     * Loads information about terms that have the specified term as object of assertion of the specified property.
     *
     * @param term     Assertion object
     * @param property Property
     * @param exclude  Terms to exclude from the result
     * @return Set of matching terms
     */
    private Set<TermInfo> loadInverseTermInfo(HasIdentifier term, String property, Collection<TermInfo> exclude) {
        final List<TermInfo> result = em.createNativeQuery("SELECT ?inverse WHERE {" +
                                                                   "?inverse ?property ?term ;" +
                                                                   "a ?type ." +
                                                                   "FILTER (?inverse NOT IN (?exclude))" +
                                                                   "} ORDER BY ?inverse", TermInfo.class)
                                        .setParameter("property", URI.create(property))
                                        .setParameter("term", term)
                                        .setParameter("type", typeUri)
                                        .setParameter("exclude", exclude)
                                        .getResultList();
        result.sort(termInfoComparator);
        return new LinkedHashSet<>(result);
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetry of SKOS relatedMatch.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseRelatedMatchTerms(Term term) {
        return loadInverseTermInfo(term, SKOS.RELATED_MATCH, term.getRelatedMatch() != null ? term
                .getRelatedMatch() : Collections.emptySet());
    }

    /**
     * Loads terms whose exact match to the specified term is inferred due to the symmetry of SKOS exactMatch.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseExactMatchTerms(Term term) {
        return loadInverseTermInfo(term, SKOS.EXACT_MATCH, term.getExactMatchTerms() != null ? term
                .getExactMatchTerms() : Collections.emptySet());
    }

    @Override
    public void persist(Term entity) {
        throw new UnsupportedOperationException(
                "Persisting term by itself is not supported. It has to be persisted into a vocabulary.");
    }

    /**
     * Persists the specified term into the specified vocabulary.
     *
     * @param entity     The term to persist
     * @param vocabulary Vocabulary which shall contain the persisted term
     */
    @ModifiesData
    public void persist(Term entity, Vocabulary vocabulary) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(vocabulary);

        try {
            entity.setGlossary(vocabulary.getGlossary().getUri());
            entity.setVocabulary(null); // This is inferred
            em.persist(entity, descriptorFactory.termDescriptor(vocabulary));
            evictCachedSubTerms(Collections.emptySet(), entity.getParentTerms());
            eventPublisher.publishEvent(new VocabularyContentModifiedEvent(this, vocabulary.getUri()));
            eventPublisher.publishEvent(new AssetPersistEvent(this, entity));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public Term update(Term entity) {
        Objects.requireNonNull(entity);
        assert entity.getVocabulary() != null;

        try {
            evictPossiblyCachedReferences(entity);
            final Term original = em.find(Term.class, entity.getUri(), descriptorFactory.termDescriptor(entity));
            entity.setDefinitionSource(original.getDefinitionSource());
            eventPublisher.publishEvent(new AssetUpdateEvent(this, entity));
            evictCachedSubTerms(original.getParentTerms(), entity.getParentTerms());
            final Term result = em.merge(entity, descriptorFactory.termDescriptor(entity));
            eventPublisher.publishEvent(new VocabularyContentModifiedEvent(this, original.getVocabulary()));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Evicts possibly cached instance loaded from the default context, as well as references to the instance from sub
     * terms and parents.
     *
     * @param term Entity to evict
     */
    private void evictPossiblyCachedReferences(Term term) {
        em.getEntityManagerFactory().getCache().evict(Term.class, term.getUri(), null);
        em.getEntityManagerFactory().getCache().evict(TermDto.class, term.getUri(), null);
        em.getEntityManagerFactory().getCache().evict(TermInfo.class, term.getUri(), null);
        Utils.emptyIfNull(term.getParentTerms()).forEach(pt -> {
            em.getEntityManagerFactory().getCache().evict(Term.class, pt.getUri(), null);
            em.getEntityManagerFactory().getCache().evict(TermDto.class, pt.getUri(), null);
            subTermsCache.evict(pt.getUri());
        });
        term.setSubTerms(getSubTerms(term));
        // Should be replaced by implementation of https://github.com/kbss-cvut/jopa/issues/92
        Utils.emptyIfNull(term.getSubTerms())
             .forEach(st -> {
                 em.getEntityManagerFactory().getCache().evict(Term.class, st.getUri(), null);
                 em.getEntityManagerFactory().getCache().evict(TermDto.class, st.getUri(), null);
             });
    }

    /**
     * Sets state of the specified term to the specified value.
     *
     * @param term  Term whose state to update
     * @param state State to set
     */
    public void setState(Term term, URI state) {
        term.setState(state);
        eventPublisher.publishEvent(new AssetUpdateEvent(this, term));
        evictPossiblyCachedReferences(term);
        em.createNativeQuery("DELETE {" +
                                     "?t ?hasState ?oldState ." +
                                     "} INSERT {" +
                                     "GRAPH ?g {" +
                                     "?t ?hasState ?newState ." +
                                     "}} WHERE {" +
                                     "OPTIONAL {?t ?hasState ?oldState .}" +
                                     "GRAPH ?g {" +
                                     "?t ?inScheme ?glossary ." +
                                     "}}").setParameter("t", term)
          .setParameter("hasState", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_stav_pojmu))
          .setParameter("inScheme", URI.create(SKOS.IN_SCHEME))
          .setParameter("newState", state).executeUpdate();
    }

    private void evictCachedSubTerms(Set<? extends AbstractTerm> originalParents,
                                     Set<? extends AbstractTerm> newParents) {
        final Set<AbstractTerm> originalCopy = new HashSet<>(Utils.emptyIfNull(originalParents));
        final Set<AbstractTerm> newCopy = new HashSet<>(Utils.emptyIfNull(newParents));
        originalCopy.removeAll(newCopy);
        newCopy.removeAll(originalCopy);
        originalCopy.forEach(t -> subTermsCache.evict(t.getUri()));
        newCopy.forEach(t -> subTermsCache.evict(t.getUri()));
    }

    /**
     * Finds all terms in the specified vocabulary, regardless of their position in the term hierarchy.
     *
     * @param vocabulary Vocabulary whose terms to retrieve. A reference is sufficient
     * @return List of vocabulary term DTOs
     */
    public List<TermDto> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return executeQueryAndLoadSubTerms(em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                            "GRAPH ?context { " +
                                                                            "?term a ?type ;" +
                                                                            "?hasLabel ?label ;" +
                                                                            "FILTER (lang(?label) = ?labelLang) ." +
                                                                            "}" +
                                                                            "?term ?inVocabulary ?vocabulary ." +
                                                                            " } ORDER BY " + orderSentence("?label"),
                                                                    TermDto.class)
                                                 .setParameter("context", context(vocabulary))
                                                 .setParameter("type", typeUri)
                                                 .setParameter("vocabulary", vocabulary.getUri())
                                                 .setParameter("hasLabel", LABEL_PROP)
                                                 .setParameter("inVocabulary",
                                                               URI.create(
                                                                       cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                 .setParameter("labelLang", config.getLanguage()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private URI context(Vocabulary vocabulary) {
        return contextMapper.getVocabularyContext(vocabulary);
    }

    /**
     * Gets all terms on the specified vocabulary.
     * <p>
     * No differences are made between root terms and terms with parents.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return Matching terms, ordered by label
     */
    public List<Term> findAllFull(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            // Load terms one by one. This works around the issue of terms being loaded in the persistence context
            // as Term and TermInfo, which results in IndividualAlreadyManagedExceptions from JOPA
            // The workaround relies on clearing the EntityManager after loading each term
            // The price for this solution is that this method performs very poorly for larger vocabularies (hundreds of terms)
            final List<URI> termIris = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                    "GRAPH ?context { " +
                                                                    "?term a ?type ;" +
                                                                    "?hasLabel ?label ;" +
                                                                    "FILTER (lang(?label) = ?labelLang) ." +
                                                                    "}" +
                                                                    "?term ?inVocabulary ?vocabulary ." +
                                                                    " } ORDER BY " + orderSentence("?label"), URI.class)
                                         .setParameter("type", typeUri)
                                         .setParameter("context", context(vocabulary))
                                         .setParameter("vocabulary", vocabulary.getUri())
                                         .setParameter("hasLabel", LABEL_PROP)
                                         .setParameter("inVocabulary",
                                                       URI.create(
                                                               cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                         .setParameter("labelLang", config.getLanguage()).getResultList();
            return termIris.stream().map(ti -> {
                final Term t = find(ti).get();
                em.clear();
                return t;
            }).collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T extends AbstractTerm> List<T> executeQueryAndLoadSubTerms(TypedQuery<T> query) {
        // Clear the persistence context after executing the query and before loading subterms for each of the results
        // This should prevent frequent IndividualAlreadyManagerExceptions thrown by the UoW
        // These exceptions are caused by the UoW containing the individuals typically as TermDtos (results of the query)
        // and JOPA then attempting to load them as TermInfo because they are children of some other term already managed
        // This strategy is obviously not very efficient in terms of performance but until JOPA supports read-only
        // transactions, this is probably the only way to prevent the aforementioned exceptions from appearing
        final List<T> result = query.getResultList();
        em.clear();
        result.forEach(t -> t.setSubTerms(getSubTerms(t)));
        return result;
    }

    /**
     * Gets sub-term info for the specified parent term.
     *
     * @param parent Parent term
     */
    private Set<TermInfo> getSubTerms(HasIdentifier parent) {
        return subTermsCache.getOrCompute(parent.getUri(),
                                          (k) -> loadInverseTermInfo(parent, SKOS.BROADER, Collections.emptySet()));
    }

    /**
     * Gets all terms from the specified vocabulary and any of its imports (transitively).
     * <p>
     * No differences are made between root terms and terms with parents.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return Matching terms, ordered by label
     */
    public List<TermDto> findAllIncludingImported(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ;" +
                                                                 "?inVocabulary ?parent ." +
                                                                 "?vocabulary ?imports* ?parent ." +
                                                                 "FILTER (lang(?label) = ?labelLang) ." +
                                                                 "} ORDER BY " + orderSentence("?label"), TermDto.class)
                                      .setParameter("type", typeUri)
                                      .setParameter("hasLabel", LABEL_PROP)
                                      .setParameter("inVocabulary",
                                                    URI.create(
                                                            cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                      .setParameter("imports",
                                                    URI.create(
                                                            cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                                      .setParameter("vocabulary", vocabulary.getUri())
                                      .setParameter("labelLang", config.getLanguage());
        return executeQueryAndLoadSubTerms(query);
    }

    /**
     * Loads a page of root terms (terms without a parent) contained in the specified vocabulary.
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param vocabulary   Vocabulary whose root terms should be returned
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     * @see #findAllRootsIncludingImports(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRoots(Vocabulary vocabulary, Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "SELECT DISTINCT ?term ?hasLocaleLabel WHERE {" +
                                                                 "GRAPH ?context { " +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                                                                 "BIND((lang(?label) = ?labelLang) as ?hasLocaleLabel) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "}} ORDER BY DESC(?hasLocaleLabel) lang(?label) " + orderSentence(
                                                                 "?label") + "}",
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("context", context(vocabulary))
                         .setParameter("vocabulary", vocabulary.getUri())
                         .setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setMaxResults(pageSpec.getPageSize())
                         .setFirstResult((int) pageSpec.getOffset()));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private String orderSentence(String var) {
        if (Objects.equals(config.getLanguage(), "cs")) {
            return
                    r(r(r(r(r(r(r(r(r(r(r(r(r(r("lcase(" + var + ")",
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
        return "lcase(" + var + ")";
    }

    private static String r(String string, String from, String to) {
        return "replace(" + string + ", " + from + ", " + to + ")";
    }

    /**
     * Loads a page of root terms (terms without a parent).
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     * @see #findAllRootsIncludingImports(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRoots(Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(pageSpec);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "SELECT DISTINCT ?term ?hasLocaleLabel WHERE {" +
                                                                 "?term a ?type ; " +
                                                                 "?hasLabel ?label . " +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term . " +
                                                                 "BIND((lang(?label) = ?labelLang) as ?hasLocaleLabel) ." +
                                                                 "FILTER (?term NOT IN (?included)) . " +
                                                                 "FILTER NOT EXISTS {?term a ?snapshot .} " +
                                                                 "} ORDER BY DESC(?hasLocaleLabel) lang(?label) " + orderSentence(
                                                                 "?label") + "}",
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu))
                         .setMaxResults(pageSpec.getPageSize())
                         .setFirstResult((int) pageSpec.getOffset()));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T> TypedQuery<T> setCommonFindAllRootsQueryParams(TypedQuery<T> query, boolean includeImports) {
        final TypedQuery<T> tq = query.setParameter("type", typeUri)
                                      .setParameter("hasLabel", LABEL_PROP)
                                      .setParameter("hasGlossary",
                                                    URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                                      .setParameter("hasTerm", URI.create(SKOS.HAS_TOP_CONCEPT));
        if (includeImports) {
            tq.setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik));
        }
        return tq;
    }

    private List<TermDto> loadIncludedTerms(Collection<URI> includeTerms) {
        // Clear the persistence context after executing the query and before loading included terms
        // This should prevent frequent IndividualAlreadyManagerExceptions thrown by the UoW
        // These exceptions are caused by the UoW containing the individuals typically as TermDtos (results of the query)
        // and JOPA then attempting to load them as TermInfo because they are children of some other term already managed
        // This strategy is obviously not very efficient in terms of performance but until JOPA supports read-only
        // transactions, this is probably the only way to prevent the aforementioned exceptions from appearing
        em.clear();
        final List<TermDto> result = includeTerms.stream().map(u -> em.find(TermDto.class, u))
                                                 .filter(Objects::nonNull)
                                                 .collect(Collectors.toList());
        em.clear();
        result.forEach(this::recursivelyLoadParentTermSubTerms);
        return result;
    }

    /**
     * Recursively loads subterms for the specified term and its parents (if they exist).
     * <p>
     * This implementation ensures that the term hierarchy can be traversed both ways for the specified term. This has
     * to be done to allow the tree-select component on the frontend to work properly and display the terms.
     *
     * @param term The term to load subterms for
     */
    private void recursivelyLoadParentTermSubTerms(TermDto term) {
        term.setSubTerms(getSubTerms(term));
        if (term.hasParentTerms()) {
            term.getParentTerms().forEach(this::recursivelyLoadParentTermSubTerms);
        }
    }

    /**
     * Loads a page of root terms contained in the specified vocabulary or any of its imports (transitively).
     * <p>
     * This method basically does a transitive closure of the vocabulary import relationship and retrieves a page of
     * root terms from this closure.
     * <p>
     * Terms with a label in the instance language are prepended.
     *
     * @param vocabulary The last vocabulary in the vocabulary import chain
     * @param pageSpec   Page specification
     * @return Matching terms, ordered by their label
     * @see #findAllRoots(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRootsIncludingImports(Vocabulary vocabulary, Pageable pageSpec,
                                                      Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "SELECT DISTINCT ?term ?hasLocaleLabel WHERE {" +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?imports* ?parent ." +
                                                                 "?parent ?hasGlossary/?hasTerm ?term ." +
                                                                 "BIND((lang(?label) = ?labelLang) as ?hasLocaleLabel) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "} ORDER BY DESC(?hasLocaleLabel) lang(?label) " + orderSentence(
                                                                 "?label") + "}",
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, true);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("vocabulary", vocabulary.getUri())
                         .setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setFirstResult((int) pageSpec.getOffset())
                         .setMaxResults(pageSpec.getPageSize()));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds terms whose label contains the specified search string.
     * <p>
     * This method searches in the specified vocabulary only.
     *
     * @param searchString String the search term labels by
     * @param vocabulary   Vocabulary whose terms should be searched
     * @return List of matching terms
     */
    public List<TermDto> findAll(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        final TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                       "GRAPH ?context { " +
                                                                       "?term a ?type ; " +
                                                                       "      ?hasLabel ?label ; " +
                                                                       "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                                                                       "}" +
                                                                       "?term ?inVocabulary ?vocabulary ." +
                                                                       "} ORDER BY " + orderSentence("?label"),
                                                               TermDto.class)
                                            .setParameter("type", typeUri)
                                            .setParameter("context", context(vocabulary))
                                            .setParameter("hasLabel", LABEL_PROP)
                                            .setParameter("inVocabulary", URI.create(
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                            .setParameter("vocabulary", vocabulary.getUri())
                                            .setParameter("searchString", searchString, config.getLanguage());
        try {
            final List<TermDto> terms = executeQueryAndLoadSubTerms(query);
            terms.forEach(this::loadParentSubTerms);
            return terms;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds terms whose label contains the specified search string.
     *
     * @param searchString String the search term labels by
     * @return List of matching terms
     */
    public List<TermDto> findAll(String searchString) {
        Objects.requireNonNull(searchString);
        final TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                       "?term a ?type ; " +
                                                                       "      ?hasLabel ?label ; " +
                                                                       "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                                                                       "?term ?inVocabulary ?vocabulary . " +
                                                                       "FILTER NOT EXISTS {?term a ?snapshot . }" +
                                                                       "} ORDER BY " + orderSentence("?label"),
                                                               TermDto.class)
                                            .setParameter("type", typeUri)
                                            .setParameter("hasLabel", LABEL_PROP)
                                            .setParameter("inVocabulary", URI.create(
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                            .setParameter("snapshot", URI.create(
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu))
                                            .setParameter("searchString", searchString, config.getLanguage());

        try {
            final List<TermDto> terms = executeQueryAndLoadSubTerms(query);
            terms.forEach(this::loadParentSubTerms);
            return terms;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private void loadParentSubTerms(TermDto parent) {
        parent.setSubTerms(getSubTerms(parent));
        if (parent.getParentTerms() != null) {
            parent.getParentTerms().forEach(this::loadParentSubTerms);
        }
    }

    /**
     * Finds terms whose label contains the specified search string.
     * <p>
     * This method searches in the specified vocabulary and all the vocabularies it (transitively) imports.
     *
     * @param searchString String the search term labels by
     * @param vocabulary   Vocabulary whose terms should be searched
     * @return List of matching terms
     */
    public List<TermDto> findAllIncludingImported(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        final TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                       "?targetVocabulary ?imports* ?vocabulary ." +
                                                                       "?term a ?type ;\n" +
                                                                       "      ?hasLabel ?label ;\n" +
                                                                       "      ?inVocabulary ?vocabulary ." +
                                                                       "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) .\n" +
                                                                       "} ORDER BY " + orderSentence("?label"),
                                                               TermDto.class)
                                            .setParameter("type", typeUri)
                                            .setParameter("hasLabel", LABEL_PROP)
                                            .setParameter("inVocabulary", URI.create(
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                            .setParameter("imports",
                                                          URI.create(
                                                                  cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                                            .setParameter("targetVocabulary", vocabulary.getUri())
                                            .setParameter("searchString", searchString, config.getLanguage());
        try {
            final List<TermDto> terms = executeQueryAndLoadSubTerms(query);
            terms.forEach(this::loadParentSubTerms);
            return terms;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Checks whether a term with the specified label exists in a vocabulary with the specified URI.
     * <p>
     * Note that this method uses comparison ignoring case, so that two labels differing just in character case are
     * considered same here.
     *
     * @param label       Label to check
     * @param vocabulary  Vocabulary in which terms will be searched
     * @param languageTag Language tag of the label, optional. If {@code null}, any language is accepted
     * @return Whether term with {@code label} already exists in vocabulary
     */
    public boolean existsInVocabulary(String label, Vocabulary vocabulary, String languageTag) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(vocabulary);
        try {
            return em.createNativeQuery("ASK { ?term a ?type ; " +
                                                "?hasLabel ?label ;" +
                                                "?inVocabulary ?vocabulary ." +
                                                "FILTER (LCASE(?label) = LCASE(?searchString)) . "
                                                + "}", Boolean.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", LABEL_PROP)
                     .setParameter("inVocabulary",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("vocabulary", vocabulary.getUri())
                     .setParameter("searchString", label,
                                   languageTag != null ? languageTag : config.getLanguage()).getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets the identifier of a term with the specified label in a vocabulary with the specified URI.
     * <p>
     * Note that this method uses comparison ignoring case, so that two labels differing just in character case are
     * considered same here.
     *
     * @param label       Label to search by
     * @param vocabulary  Vocabulary in which terms will be searched
     * @param languageTag Language tag of the label
     * @return Identifier of matching term wrapped in an {@code Optional}, empty {@code Optional} if there is no such
     * term
     */
    public Optional<URI> findIdentifierByLabel(String label, Vocabulary vocabulary, String languageTag) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(vocabulary);
        try {
            return Optional.of(em.createNativeQuery("SELECT ?term { ?term a ?type ; " +
                                                "?hasLabel ?label ;" +
                                                "?inVocabulary ?vocabulary ." +
                                                "FILTER (LCASE(?label) = LCASE(?searchString)) . "
                                                + "}", URI.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", LABEL_PROP)
                     .setParameter("inVocabulary",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("vocabulary", vocabulary.getUri())
                     .setParameter("searchString", label,
                                   languageTag != null ? languageTag : config.getLanguage()).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of all terms in the specified vocabulary that have no occurrences (file or definitional).
     *
     * @param vocabulary Vocabulary whose terms to examine
     * @return List of unused terms identifiers
     */
    public List<URI> findAllUnused(Vocabulary vocabulary) {
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
                 .setParameter("hasTerm", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_prirazenim_termu))
                 .setParameter("hasTarget", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_cil))
                 .setParameter("hasSource", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zdroj))
                 .getResultList();
    }

    @ModifiesData
    @Override
    public void remove(Term entity) {
        super.remove(entity);
        evictCachedSubTerms(entity.getParentTerms(), Collections.emptySet());
        eventPublisher.publishEvent(new VocabularyContentModifiedEvent(this, entity.getVocabulary()));
    }

    @Override
    public List<Snapshot> findSnapshots(Term asset) {
        return new AssetSnapshotLoader<Term>(em, typeUri, URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu)).findSnapshots(asset);
    }

    @Override
    public Optional<Term> findVersionValidAt(Term asset, Instant at) {
        return new AssetSnapshotLoader<Term>(em, typeUri, URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu))
                .findVersionValidAt(asset, at).map(t -> {
                    postLoad(t);
                    return t;
                });
    }

    @EventListener
    public void onEvictCache(EvictCacheEvent evt) {
        subTermsCache.evictAll();
    }
}

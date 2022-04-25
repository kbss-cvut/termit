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
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.util.Cache;
import cz.cvut.kbss.termit.persistence.dao.util.SparqlResultToTermInfoMapper;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class TermDao extends AssetDao<Term> {

    private static final URI LABEL_PROP = URI.create(SKOS.PREF_LABEL);

    private final Cache<URI, Set<TermInfo>> subTermsCache;

    private final Comparator<TermInfo> termInfoComparator;

    @Autowired
    public TermDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory,
                   Cache<URI, Set<TermInfo>> subTermsCache) {
        super(Term.class, em, config.getPersistence(), descriptorFactory);
        this.subTermsCache = subTermsCache;
        this.termInfoComparator = Comparator.comparing(t -> t.getLabel().get(config.getPersistence().getLanguage()));
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROP;
    }

    @Override
    public Optional<Term> find(URI id) {
        final Optional<Term> result = super.find(id);
        result.ifPresent(r -> {
            r.setSubTerms(getSubTerms(r));
            r.setInverseRelated(loadInverseRelatedTerms(r));
            r.setInverseRelatedMatch(loadInverseRelatedMatchTerms(r));
            r.setInverseExactMatchTerms(loadInverseExactMatchTerms(r));
        });
        return result;
    }

    public void detach(Term term) {
        Objects.requireNonNull(term);
        em.detach(term);
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetry of SKOS related.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseRelatedTerms(Term term) {
        return loadTermInfo(term, SKOS.RELATED, Utils.joinCollections(term.getRelated(), term.getRelatedMatch()));
    }

    private Set<TermInfo> loadTermInfo(Term term, String property, Collection<TermInfo> exclude) {
        final List<?> inverse = em.createNativeQuery("SELECT ?inverse ?label ?vocabulary WHERE {" +
                                                             "?inverse ?property ?term ;" +
                                                             "a ?type ;" +
                                                             "?hasLabel ?label ;" +
                                                             "?inVocabulary ?vocabulary . " +
                                                             "FILTER (?inverse NOT IN (?exclude))" +
                                                             "} ORDER BY ?inverse")
                                  .setParameter("property", URI.create(property))
                                  .setParameter("term", term)
                                  .setParameter("type", typeUri)
                                  .setParameter("hasLabel", labelProperty())
                                  .setParameter("inVocabulary", URI
                                          .create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                  .setParameter("exclude", exclude)
                                  .getResultList();
        final List<TermInfo> result = new SparqlResultToTermInfoMapper().map(inverse);
        result.sort(termInfoComparator);
        return new LinkedHashSet<>(result);
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetric of SKOS relatedMatch.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseRelatedMatchTerms(Term term) {
        return loadTermInfo(term, SKOS.RELATED_MATCH, term.getRelatedMatch() != null ? term
                .getRelatedMatch() : Collections.emptySet());
    }

    /**
     * Loads terms whose exact match to the specified term is inferred due to the symmetric of SKOS exactMatch.
     *
     * @param term Term to load related terms for
     */
    private Set<TermInfo> loadInverseExactMatchTerms(Term term) {
        return loadTermInfo(term, SKOS.EXACT_MATCH, term.getExactMatchTerms() != null ? term
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
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Term.class, entity.getUri(), null);
            em.getEntityManagerFactory().getCache().evict(TermDto.class, entity.getUri(), null);
            final Term original = em.find(Term.class, entity.getUri(), descriptorFactory.termDescriptor(entity));
            entity.setDefinitionSource(original.getDefinitionSource());
            evictCachedSubTerms(original.getParentTerms(), entity.getParentTerms());
            return em.merge(entity, descriptorFactory.termDescriptor(entity));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Marks the specified term as draft.
     *
     * @param term Term to mark as draft
     */
    public void setAsDraft(Term term) {
        Objects.requireNonNull(term);
        setTermDraftStatusTo(term, true);
    }


    private void setTermDraftStatusTo(Term term, boolean draft) {
        // Evict possibly cached instance loaded from default context
        em.getEntityManagerFactory().getCache().evict(Term.class, term.getUri(), null);
        em.getEntityManagerFactory().getCache().evict(TermDto.class, term.getUri(), null);
        em.createNativeQuery("DELETE {" +
                                     "?t ?hasStatus ?oldDraft ." +
                                     "} INSERT {" +
                                     "GRAPH ?g {" +
                                     "?t ?hasStatus ?newDraft ." +
                                     "}} WHERE {" +
                                     "OPTIONAL {?t ?hasStatus ?oldDraft .}" +
                                     "GRAPH ?g {" +
                                     "?t ?inScheme ?glossary ." +
                                     "}}").setParameter("t", term)
          .setParameter("hasStatus", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_draft))
          .setParameter("inScheme", URI.create(SKOS.IN_SCHEME))
          .setParameter("newDraft", draft).executeUpdate();
    }

    /**
     * Marks the specified term as confirmed.
     *
     * @param term Term to mark as confirmed
     */
    public void setAsConfirmed(Term term) {
        Objects.requireNonNull(term);
        setTermDraftStatusTo(term, false);
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

    public List<TermDto> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return executeQueryAndLoadSubTerms(em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                            "GRAPH ?vocabulary { " +
                                                                            "?term a ?type ;" +
                                                                            "?hasLabel ?label ;" +
                                                                            "FILTER (lang(?label) = ?labelLang) ." +
                                                                            "}" +
                                                                            "?term ?inVocabulary ?vocabulary ." +
                                                                            " } ORDER BY " + orderSentence(
                                                         config.getLanguage(), "?label"), TermDto.class)
                                                 .setParameter("type", typeUri)
                                                 .setParameter("vocabulary", vocabulary)
                                                 .setParameter("hasLabel", LABEL_PROP)
                                                 .setParameter("inVocabulary",
                                                               URI.create(
                                                                       cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                 .setParameter("labelLang", config.getLanguage()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
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
            // The workaround relies on the fact that jopa-spring-transaction will create a new persistence context
            // for each find call
            // The price for this solution is that this method performs very poorly for larger vocabularies (hundreds of terms)
            final List<URI> termIris = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                    "GRAPH ?vocabulary { " +
                                                                    "?term a ?type ;" +
                                                                    "?hasLabel ?label ;" +
                                                                    "FILTER (lang(?label) = ?labelLang) ." +
                                                                    "}" +
                                                                    "?term ?inVocabulary ?vocabulary ." +
                                                                    " } ORDER BY " + orderSentence(config.getLanguage(),
                                                                                                   "?label"), URI.class)
                                         .setParameter("type", typeUri)
                                         .setParameter("vocabulary", vocabulary.getUri())
                                         .setParameter("hasLabel", LABEL_PROP)
                                         .setParameter("inVocabulary",
                                                       URI.create(
                                                               cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                         .setParameter("labelLang", config.getLanguage()).getResultList();
            return termIris.stream().map(ti -> find(ti).get()).collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Returns true if the vocabulary does not contain any terms.
     *
     * @param vocabulary Vocabulary to check for existence of terms
     * @return true, if the vocabulary contains no terms, false otherwise
     */
    public boolean isEmpty(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return !em.createNativeQuery("ASK WHERE {" +
                                                 "GRAPH ?vocabulary { " +
                                                 "?term a ?type ;" +
                                                 "}" +
                                                 "?term ?inVocabulary ?vocabulary ." +
                                                 " }", Boolean.class)
                      .setParameter("type", typeUri)
                      .setParameter("vocabulary", vocabulary.getUri())
                      .setParameter("inVocabulary",
                                    URI.create(
                                            cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                      .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T extends AbstractTerm> List<T> executeQueryAndLoadSubTerms(TypedQuery<T> query) {
        return query.getResultStream().peek(t -> t.setSubTerms(getSubTerms(t))).collect(Collectors.toList());
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
                                                                 "} ORDER BY " + orderSentence(config.getLanguage(),
                                                                                               "?label"), TermDto.class)
                                      .setParameter("type", typeUri)
                                      .setParameter("hasLabel", LABEL_PROP)
                                      .setParameter("inVocabulary",
                                                    URI.create(
                                                            cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                      .setParameter("imports",
                                                    URI.create(
                                                            cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                                      .setParameter("vocabulary", vocabulary)
                                      .setParameter("labelLang", config.getLanguage());
        return executeQueryAndLoadSubTerms(query);
    }

    /**
     * Gets sub-term info for the specified parent term.
     *
     * @param parent Parent term
     */
    private Set<TermInfo> getSubTerms(HasIdentifier parent) {
        return subTermsCache.getOrCompute(parent.getUri(), this::loadSubTerms);
    }

    /**
     * Actually loads sub-terms of a term with the specified identifiers.
     *
     * @param parentUri Parent term identifier
     * @return Set of sub-terms, sorted by label
     */
    private Set<TermInfo> loadSubTerms(URI parentUri) {
        final List<?> subTerms = em.createNativeQuery("SELECT ?entity ?label ?vocabulary WHERE {" +
                                                              "?parent ?narrower ?entity ." +
                                                              "?entity a ?type ;" +
                                                              "?hasLabel ?label ;" +
                                                              "?inVocabulary ?vocabulary . } ORDER BY ?entity")
                                   .setParameter("type", typeUri)
                                   .setParameter("narrower", URI.create(SKOS.NARROWER))
                                   .setParameter("parent", parentUri)
                                   .setParameter("hasLabel", LABEL_PROP)
                                   .setParameter("inVocabulary", URI
                                           .create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                   .getResultList();
        final List<TermInfo> result = new SparqlResultToTermInfoMapper().map(subTerms);
        result.sort(termInfoComparator);
        return new LinkedHashSet<>(result);
    }

    /**
     * Loads a page of root terms (terms without a parent) contained in the specified vocabulary.
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
                                                                 "GRAPH ?vocabulary { " +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                                                                 "FILTER (lang(?label) = ?labelLang) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "}} ORDER BY " + orderSentence(config.getLanguage(),
                                                                                                "?label"),
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("vocabulary", vocabulary.getUri())
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

    private String orderSentence(String lang, String var) {
        if ("cs".equals(lang)) {
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

    private String r(String string, String from, String to) {
        return "replace(" + string + ", " + from + ", " + to + ")";
    }

    /**
     * Loads a page of root terms (terms without a parent).
     *
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     * @see #findAllRootsIncludingImports(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRoots(Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(pageSpec);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                                                                 "FILTER (lang(?label) = ?labelLang) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "} ORDER BY " + orderSentence(config.getLanguage(),
                                                                                               "?label"),
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
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
        return includeTerms.stream().map(u -> em.find(TermDto.class, u))
                           .filter(Objects::nonNull)
                           .peek(this::recursivelyLoadParentTermSubTerms)
                           .collect(Collectors.toList());
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
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?imports* ?parent ." +
                                                                 "?parent ?hasGlossary/?hasTerm ?term ." +
                                                                 "FILTER (lang(?label) = ?labelLang) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "} ORDER BY " + orderSentence(config.getLanguage(),
                                                                                               "?label"),
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
                                                                       "GRAPH ?vocabulary { " +
                                                                       "?term a ?type ; " +
                                                                       "      ?hasLabel ?label ; " +
                                                                       "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                                                                       "}" +
                                                                       "?term ?inVocabulary ?vocabulary ." +
                                                                       "} ORDER BY " + orderSentence(
                                                    config.getLanguage(), "?label"), TermDto.class)
                                            .setParameter("type", typeUri)
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
                                                                       "GRAPH ?vocabulary { " +
                                                                       "?term a ?type ; " +
                                                                       "      ?hasLabel ?label ; " +
                                                                       "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                                                                       "}" +
                                                                       "?term ?inVocabulary ?vocabulary ." +
                                                                       "} ORDER BY " + orderSentence(
                                                    config.getLanguage(), "?label"), TermDto.class)
                                            .setParameter("type", typeUri)
                                            .setParameter("hasLabel", LABEL_PROP)
                                            .setParameter("inVocabulary", URI.create(
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
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
                                                                       "} ORDER BY " + orderSentence(
                                                    config.getLanguage(), "?label"), TermDto.class)
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
     * @param label      Label to check
     * @param vocabulary Vocabulary in which terms will be searched
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
                     .setParameter("vocabulary", vocabulary)
                     .setParameter("searchString", label,
                                   languageTag != null ? languageTag : config.getLanguage()).getSingleResult();
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

    @Override
    public void remove(Term entity) {
        super.remove(entity);
        evictCachedSubTerms(entity.getParentTerms(), Collections.emptySet());
    }
}

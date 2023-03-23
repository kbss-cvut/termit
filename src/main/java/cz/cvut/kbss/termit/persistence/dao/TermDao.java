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

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.exception.NotFoundException;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.util.*;
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
        this.termInfoComparator = Comparator.comparing(t -> t.getLabel().get(config.getPersistence().getLanguage()));
        this.contextMapper = contextMapper;
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROP;
    }

    @Override
    public Optional<Term> find(URI id) {
        Objects.requireNonNull(id);
        try {
            final Descriptor loadingDescriptor = descriptorFactory.termDescriptor(resolveTermVocabulary(id));
            final Optional<Term> result = Optional.ofNullable(em.find(type, id, loadingDescriptor));
            result.ifPresent(this::postLoad);
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private URI resolveTermVocabulary(URI termUri) {
        try {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?t ?inVocabulary ?v . }", URI.class)
                     .setParameter("inVocabulary",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("t", termUri)
                     .getSingleResult();
        } catch (NoResultException e) {
            throw NotFoundException.create(Term.class, termUri);
        }
    }

    private void postLoad(Term r) {
        final Descriptor descriptor = descriptorFactory.termInfoDescriptor(findAllVocabularies().toArray(new URI[]{}));
        r.setSubTerms(getSubTerms(r, descriptor));
        r.setInverseRelated(loadInverseRelatedTerms(r, descriptor));
        r.setInverseRelatedMatch(loadInverseRelatedMatchTerms(r, descriptor));
        r.setInverseExactMatchTerms(loadInverseExactMatchTerms(r, descriptor));
    }

    @Override
    public boolean exists(URI id) {
        try {
            return em.createNativeQuery("ASK { GRAPH ?context { ?t a ?type } }", Boolean.class)
                     .setParameter("context", resolveTermVocabulary(id))
                     .setParameter("t", id)
                     .setParameter("type", typeUri).getSingleResult();
        } catch (NotFoundException e) {
            // Thrown by resolveTermVocabulary when the term does not exist
            return false;
        }
    }

    public void detach(Term term) {
        Objects.requireNonNull(term);
        em.detach(term);
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetry of SKOS related.
     *
     * @param term              Term to load related terms for
     * @param loadingDescriptor Descriptor for the loaded related terms
     */
    private Set<TermInfo> loadInverseRelatedTerms(Term term, Descriptor loadingDescriptor) {
        return loadInverseTermInfo(term, SKOS.RELATED,
                                   Utils.joinCollections(term.getRelated(), term.getRelatedMatch()), loadingDescriptor);
    }

    /**
     * Loads information about terms that have the specified term as object of assertion of the specified property.
     *
     * @param term       Assertion object
     * @param property   Property
     * @param exclude    Terms to exclude from the result
     * @param descriptor Descriptor for the loaded terms. Used to determine contexts to load the relationships and terms
     *                   from
     * @return Set of matching terms
     */
    private Set<TermInfo> loadInverseTermInfo(HasIdentifier term, String property, Collection<TermInfo> exclude,
                                              Descriptor descriptor) {
        final List<TermInfo> result = em.createNativeQuery("SELECT ?inverse WHERE {" +
                                                                   "GRAPH ?g { " +
                                                                   "?inverse ?property ?term . } " +
                                                                   "?inverse a ?type ." +
                                                                   "FILTER (?inverse NOT IN (?exclude))" +
                                                                   "FILTER (?g IN (?contexts))" +
                                                                   "} ORDER BY ?inverse", TermInfo.class)
                                        .setParameter("property", URI.create(property))
                                        .setParameter("term", term)
                                        .setParameter("type", typeUri)
                                        .setParameter("exclude", exclude)
                                        .setParameter("contexts", descriptor.getContexts())
                                        .setDescriptor(descriptor)
                                        .getResultList();
        result.sort(termInfoComparator);
        return new LinkedHashSet<>(result);
    }

    /**
     * Loads terms whose relatedness to the specified term is inferred due to the symmetry of SKOS relatedMatch.
     *
     * @param term              Term to load related terms for
     * @param loadingDescriptor Descriptor for the loaded terms
     */
    private Set<TermInfo> loadInverseRelatedMatchTerms(Term term, Descriptor loadingDescriptor) {
        return loadInverseTermInfo(term, SKOS.RELATED_MATCH, Utils.emptyIfNull(term.getRelatedMatch()),
                                   loadingDescriptor);
    }

    /**
     * Loads terms whose exact match to the specified term is inferred due to the symmetry of SKOS exactMatch.
     *
     * @param term              Term to load related terms for
     * @param loadingDescriptor Descriptor for the loaded terms
     */
    private Set<TermInfo> loadInverseExactMatchTerms(Term term, Descriptor loadingDescriptor) {
        return loadInverseTermInfo(term, SKOS.EXACT_MATCH, Utils.emptyIfNull(term.getExactMatchTerms()),
                                   loadingDescriptor);
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
            em.persist(entity, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
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
            // Evict possibly cached TermDto instance
            em.getEntityManagerFactory().getCache()
              .evict(TermDto.class, entity.getUri(), contextMapper.getVocabularyContext(entity.getVocabulary()));
            final Term original = em.find(Term.class, entity.getUri(), descriptorFactory.termDescriptor(entity));
            entity.setDefinitionSource(original.getDefinitionSource());
            evictCachedSubTerms(original.getParentTerms(), entity.getParentTerms());
            return em.merge(entity, descriptorFactory.termDescriptorForSave(entity));
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
        // Evict possibly cached TermDto instance
        em.getEntityManagerFactory().getCache().evict(TermDto.class, term.getUri(), null);
        em.createNativeQuery("DELETE {" +
                                     "GRAPH ?g { ?t ?hasStatus ?oldDraft . }" +
                                     "} INSERT {" +
                                     "GRAPH ?g { ?t ?hasStatus ?newDraft . }" +
                                     "} WHERE {" +
                                     "OPTIONAL { ?t ?hasStatus ?oldDraft . }" +
                                     "GRAPH ?g { ?t ?inScheme ?glossary . }" +
                                     "}").setParameter("t", term)
          .setParameter("hasStatus", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_draft))
          .setParameter("inScheme", URI.create(SKOS.IN_SCHEME))
          .setParameter("newDraft", draft)
          .setParameter("g", contextMapper.getVocabularyContext(term.getVocabulary())).executeUpdate();
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
                                                 .setParameter("vocabulary", vocabulary)
                                                 .setParameter("hasLabel", LABEL_PROP)
                                                 .setParameter("inVocabulary",
                                                               URI.create(
                                                                       cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                 .setParameter("labelLang", config.getLanguage())
                                                 .setDescriptor(
                                                         descriptorFactory.termDtoDescriptor(vocabulary.getUri())));
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
                                         .setParameter("labelLang", config.getLanguage())
                                         .setDescriptor(descriptorFactory.termDescriptor(vocabulary)).getResultList();
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
        final Descriptor descriptor = descriptorFactory.termInfoDescriptor(findAllVocabularies().toArray(new URI[]{}));
        final List<T> result = query.getResultList();
        em.clear();
        result.forEach(t -> t.setSubTerms(getSubTerms(t, descriptor)));
        return result;
    }

    /**
     * Gets sub-term info for the specified parent term.
     *
     * @param parent            Parent term
     * @param loadingDescriptor Descriptor for loading the terms
     */
    private Set<TermInfo> getSubTerms(HasIdentifier parent, Descriptor loadingDescriptor) {
        return subTermsCache.getOrCompute(parent.getUri(),
                                          (k) -> loadInverseTermInfo(parent, SKOS.BROADER, Collections.emptySet(),
                                                                     loadingDescriptor));
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
                                      .setParameter("vocabulary", vocabulary)
                                      .setParameter("labelLang", config.getLanguage())
                                      .setDescriptor(descriptorFactory.termDtoDescriptorWithImportedVocabularies(
                                              vocabulary.getUri()));
        return executeQueryAndLoadSubTerms(query);
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
                                                                 "GRAPH ?context { " +
                                                                 "?term a ?type ;" +
                                                                 "?hasLabel ?label ." +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                                                                 "FILTER (lang(?label) = ?labelLang) ." +
                                                                 "FILTER (?term NOT IN (?included))" +
                                                                 "}} ORDER BY " + orderSentence("?label"),
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("context", context(vocabulary))
                         .setParameter("vocabulary", vocabulary.getUri())
                         .setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setMaxResults(pageSpec.getPageSize())
                         .setFirstResult((int) pageSpec.getOffset())
                         .setDescriptor(descriptorFactory.termDtoDescriptor(vocabulary.getUri())));
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
     *
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     * @see #findAllRootsIncludingImports(Vocabulary, Pageable, Collection)
     */
    public List<TermDto> findAllRoots(Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(pageSpec);
        TypedQuery<TermDto> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                                                                 "?term a ?type ; " +
                                                                 "?hasLabel ?label . " +
                                                                 "?vocabulary ?hasGlossary/?hasTerm ?term . " +
                                                                 "FILTER (lang(?label) = ?labelLang) . " +
                                                                 "FILTER (?term NOT IN (?included)) . " +
                                                                 "FILTER NOT EXISTS {?term a ?snapshot .} " +
                                                                 "} ORDER BY " + orderSentence("?label"),
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, false);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu))
                         .setMaxResults(pageSpec.getPageSize())
                         .setFirstResult((int) pageSpec.getOffset())
                         .setDescriptor(
                                 descriptorFactory.termDtoDescriptor(findAllVocabularies().toArray(new URI[]{}))));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private Set<URI> findAllVocabularies() {
        return contextMapper.getVocabularyContexts().keySet();
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
        final List<TermDto> result = includeTerms.stream().map(u -> em.find(TermDto.class, u,
                                                                            descriptorFactory.termDtoDescriptor(
                                                                                    resolveTermVocabulary(u))))
                                                 .filter(Objects::nonNull)
                                                 .collect(Collectors.toList());
        em.clear();
        result.forEach(this::loadParentSubTerms);
        return result;
    }

    /**
     * Recursively loads subterms for the specified term and its parents (if they exist).
     * <p>
     * This implementation ensures that the term hierarchy can be traversed both ways for the specified term. This has
     * to be done to allow the tree-select component on the frontend to work properly and display the terms.
     *
     * @param parent The term to load subterms for
     */
    private void loadParentSubTerms(TermDto parent) {
        final Descriptor descriptor = descriptorFactory.termInfoDescriptor(findAllVocabularies().toArray(new URI[]{}));
        recursivelyLoadParentSubTerms(parent, descriptor);
    }

    private void recursivelyLoadParentSubTerms(TermDto parent, Descriptor subTermDescriptor) {
        parent.setSubTerms(getSubTerms(parent, subTermDescriptor));
        if (parent.hasParentTerms()) {
            parent.getParentTerms().forEach(pt -> recursivelyLoadParentSubTerms(pt, subTermDescriptor));
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
                                                                 "} ORDER BY " + orderSentence("?label"),
                                                         TermDto.class);
        query = setCommonFindAllRootsQueryParams(query, true);
        try {
            final List<TermDto> result = executeQueryAndLoadSubTerms(
                    query.setParameter("vocabulary", vocabulary.getUri())
                         .setParameter("labelLang", config.getLanguage())
                         .setParameter("included", includeTerms)
                         .setFirstResult((int) pageSpec.getOffset())
                         .setMaxResults(pageSpec.getPageSize())
                         .setDescriptor(
                                 descriptorFactory.termDtoDescriptorWithImportedVocabularies(vocabulary.getUri())));
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
                                            .setParameter("searchString", searchString, config.getLanguage())
                                            .setDescriptor(descriptorFactory.termDtoDescriptor(vocabulary.getUri()));
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
                                                                       "" +
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
                                            .setParameter("searchString", searchString, config.getLanguage())
                                            .setDescriptor(descriptorFactory.termDtoDescriptor(
                                                    findAllVocabularies().toArray(new URI[]{})));

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
                                            .setParameter("searchString", searchString, config.getLanguage())
                                            .setDescriptor(descriptorFactory.termDtoDescriptorWithImportedVocabularies(
                                                    vocabulary.getUri()));
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
            URI vocabularyContext = contextMapper.getVocabularyContext(vocabulary);
            return em.createNativeQuery("ASK { GRAPH ?context { " +
                                        "?term a ?type ; " +
                                        "?hasLabel ?label . } " +
                                        "?term ?inVocabulary ?vocabulary . " +
                                        "FILTER (LCASE(?label) = LCASE(?searchString)) . " +
                                        "}", Boolean.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", LABEL_PROP)
                     .setParameter("inVocabulary",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("vocabulary", vocabulary)
                     .setParameter("searchString", label,
                                   languageTag != null ? languageTag : config.getLanguage())
                     .setParameter("context", vocabularyContext)
                     .getSingleResult();
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

    @Override
    public Optional<Term> getReference(URI id) {
        Objects.requireNonNull(id);
        try {
            Descriptor descriptor = descriptorFactory.termDescriptor(resolveTermVocabulary(id));
            return Optional.ofNullable(em.getReference(type, id, descriptor));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
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
                    final Descriptor descriptor = descriptorFactory.termInfoDescriptor(findAllSnapshotVocabularies().toArray(new URI[]{}));
                    t.setSubTerms(getSubTerms(t, descriptor));
                    t.setInverseRelated(loadInverseRelatedTerms(t, descriptor));
                    t.setInverseRelatedMatch(loadInverseRelatedMatchTerms(t, descriptor));
                    t.setInverseExactMatchTerms(loadInverseExactMatchTerms(t, descriptor));
                    return t;
                });
    }

    private List<URI> findAllSnapshotVocabularies() {
        try {
            return em.createNativeQuery("SELECT DISTINCT ?vocabulary WHERE { " +
                                                "?vocabulary a ?snapshot . " +
                                                "}", URI.class)
                     .setParameter("snapshot", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku))
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}

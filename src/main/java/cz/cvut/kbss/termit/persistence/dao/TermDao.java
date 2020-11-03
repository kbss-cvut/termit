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
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceBasedAssetDao;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public class TermDao extends WorkspaceBasedAssetDao<Term> {

    private static final URI LABEL_PROP = URI.create(SKOS.PREF_LABEL);

    private final VocabularyDao vocabularyDao;

    @Autowired
    public TermDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory,
                   VocabularyDao vocabularyDao, PersistenceUtils persistenceUtils) {
        super(Term.class, em, config, descriptorFactory, persistenceUtils);
        this.vocabularyDao = vocabularyDao;
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROP;
    }

    @Override
    public Optional<Term> find(URI id) {
        try {
            final Optional<Term> result = Optional.ofNullable(
                    em.find(Term.class, id, descriptorFactory.termDescriptor(resolveVocabularyIri(id))));
            result.ifPresent(this::loadSubTerms);
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private URI resolveVocabularyIri(URI termIri) {
        return em.createNativeQuery("SELECT ?vocabulary WHERE { ?term ?inVocabulary ?vocabulary . }", URI.class)
                 .setParameter("term", termIri)
                 .setParameter("inVocabulary", URI.create(
                         cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)).getSingleResult();
    }

    /**
     * Unsupported, use {@link #persist(Term, Vocabulary)}.
     */
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
    public void persist(Term entity, Vocabulary vocabulary) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(vocabulary);

        try {
            entity.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(entity, descriptorFactory.termDescriptor(vocabulary));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Term update(Term entity) {
        Objects.requireNonNull(entity);
        assert entity.getVocabulary() != null;

        try {
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Term.class, entity.getUri(), null);
            return em.merge(entity, descriptorFactory.termDescriptor(entity.getVocabulary()));
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
    public List<Term> findAll(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            final TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                    "GRAPH ?g { " +
                    "?term a ?type ;" +
                    "?hasLabel ?label ." +
                    "FILTER (lang(?label) = ?labelLang) ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary. } ORDER BY ?label", Term.class)
                                             .setParameter("type", typeUri)
                                             .setParameter("vocabulary", vocabulary.getUri())
                                             .setParameter("g",
                                                     persistenceUtils.resolveVocabularyContext(vocabulary.getUri()))
                                             .setParameter("hasLabel", LABEL_PROP)
                                             .setParameter("inVocabulary",
                                                     URI.create(
                                                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                             .setParameter("labelLang", config.get(ConfigParam.LANGUAGE));
            query.setDescriptor(descriptorFactory.termDescriptor(vocabulary));
            return executeQueryAndLoadSubTerms(query);
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
                    "GRAPH ?g { " +
                    "?term a ?type ;" +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    " }", Boolean.class)
                      .setParameter("type", typeUri)
                      .setParameter("vocabulary", vocabulary.getUri())
                      .setParameter("g", persistenceUtils.resolveVocabularyContext(vocabulary.getUri()))
                      .setParameter("inVocabulary",
                              URI.create(
                                      cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)).getSingleResult();
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
                        cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku)).getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private List<Term> executeQueryAndLoadSubTerms(TypedQuery<Term> query) {
        final List<Term> terms = query.getResultList();
        terms.forEach(this::loadSubTerms);
        return terms;
    }

    /**
     * Gets all terms from the specified vocabulary and any of its imports (transitively).
     * <p>
     * No differences are made between root terms and terms with parents.
     *
     * @param vocabulary Vocabulary whose terms should be returned
     * @return Matching terms, ordered by label
     */
    public List<Term> findAllIncludingImported(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                "?term a ?type ;" +
                "?hasLabel ?label ;" +
                "?inVocabulary ?parent ." +
                "?vocabulary ?imports* ?parent ." +
                "FILTER (lang(?label) = ?labelLang) ." +
                "} ORDER BY ?label", Term.class).setParameter("type", typeUri)
                                   .setParameter("hasLabel", LABEL_PROP)
                                   .setParameter("inVocabulary",
                                           URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                   .setParameter("imports",
                                           URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                                   .setParameter("vocabulary", vocabulary)
                                   .setParameter("labelLang", config.get(ConfigParam.LANGUAGE));
        return executeQueryAndLoadSubTerms(query);
    }

    /**
     * Loads sub-term info for the specified parent term.
     * <p>
     * The sub-terms are set directly on the specified parent.
     *
     * @param parent Parent term
     */
    private void loadSubTerms(Term parent) {
        final Stream<TermInfo> subTermsStream = em.createNativeQuery("SELECT ?entity ?label ?vocabulary WHERE {" +
                "GRAPH ?g { ?entity ?broader ?parent ;" +
                "a ?type ;" +
                "?hasLabel ?label ." +
                "}" +
                "?entity ?inVocabulary ?vocabulary ." +
                "?mc a ?metadataCtx ;" +
                "?referencesCtx ?g ." +
                "FILTER (lang(?label) = ?labelLang) . }", "TermInfo")
                                                  .setParameter("type", typeUri)
                                                  .setParameter("broader", URI.create(SKOS.BROADER))
                                                  .setParameter("parent", parent.getUri())
                                                  .setParameter("hasLabel", LABEL_PROP)
                                                  .setParameter("inVocabulary",
                                                          URI.create(
                                                                  cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                  .setParameter("mc", persistenceUtils.getCurrentWorkspace())
                                                  .setParameter("metadataCtx",
                                                          URI.create(
                                                                  cz.cvut.kbss.termit.util.Vocabulary.s_c_metadatovy_kontext))
                                                  .setParameter("referencesCtx",
                                                          URI.create(
                                                                  cz.cvut.kbss.termit.util.Vocabulary.s_p_odkazuje_na_kontext))
                                                  .setParameter("labelLang", config.get(ConfigParam.LANGUAGE))
                                                  .getResultStream();
        parent.setSubTerms(subTermsStream.collect(Collectors.toSet()));
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
    public List<Term> findAllRoots(Vocabulary vocabulary, Pageable pageSpec, Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        return findAllRootsImpl(vocabulary.getUri(), pageSpec, includeTerms);
    }

    private List<Term> findAllRootsImpl(URI vocabularyIri, Pageable pageSpec, Collection<URI> includeTerms) {
        TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                "GRAPH ?g { " +
                "?term a ?type ;" +
                "?hasLabel ?label ." +
                "?vocabulary ?hasGlossary/?hasTerm ?term ." +
                "FILTER (lang(?label) = ?labelLang) ." +
                "}} ORDER BY ?label OFFSET ?offset LIMIT ?limit", Term.class);
        query = setCommonFindAllRootsQueryParams(query);
        query.setDescriptor(descriptorFactory.termDescriptor(vocabularyIri));
        try {
            final List<Term> result = executeQueryAndLoadSubTerms(query.setParameter("vocabulary", vocabularyIri)
                                                                       .setParameter("g",
                                                                               persistenceUtils
                                                                                       .resolveVocabularyContext(
                                                                                               vocabularyIri))
                                                                       .setParameter("labelLang",
                                                                               config.get(ConfigParam.LANGUAGE))
                                                                       .setUntypedParameter("offset",
                                                                               pageSpec.getOffset())
                                                                       .setUntypedParameter("limit",
                                                                               pageSpec.getPageSize()));
            result.addAll(loadIncludedTerms(includeTerms));
            return result;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private <T> TypedQuery<T> setCommonFindAllRootsQueryParams(TypedQuery<T> query) {
        return query.setParameter("type", typeUri)
                    .setParameter("hasLabel", LABEL_PROP)
                    .setParameter("hasGlossary",
                            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                    .setParameter("hasTerm", URI.create(SKOS.HAS_TOP_CONCEPT));
    }

    private List<Term> loadIncludedTerms(Collection<URI> includeTerms) {
        return includeTerms.stream()
                           .map(u -> em.find(Term.class, u, descriptorFactory.termDescriptor(resolveVocabularyIri(u))))
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }

    /**
     * Loads a page of root terms contained in the specified vocabulary or any of its imports (transitively).
     * <p>
     * This method basically does a transitive closure of the vocabulary import relationship and retrieves a page of
     * root terms from this closure.
     *
     * @param vocabulary   The last vocabulary in the vocabulary import chain
     * @param pageSpec     Page specification
     * @param includeTerms Identifiers of terms which should be a part of the result. Optional
     * @return Matching terms, ordered by their label
     * @see #findAllRoots(Vocabulary, Pageable, Collection)
     */
    public List<Term> findAllRootsIncludingImports(Vocabulary vocabulary, Pageable pageSpec,
                                                   Collection<URI> includeTerms) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(pageSpec);
        final Collection<URI> vocabularies = vocabularyDao.getTransitivelyImportedVocabularies(vocabulary);
        vocabularies.add(vocabulary.getUri());
        final List<Term> result = new ArrayList<>();
        vocabularies.forEach(v -> result.addAll(findAllRootsImpl(v, pageSpec, includeTerms)));
        result.sort(Comparator.comparing(Asset::getLabel));
        return result.subList(0, Math.min(result.size(), pageSpec.getPageSize()));
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
    public List<Term> findAll(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        return findAllImpl(searchString, vocabulary.getUri());
    }

    private List<Term> findAllImpl(String searchString, URI vocabularyIri) {
        try {
            final TypedQuery<Term> query = em.createNativeQuery("SELECT DISTINCT ?term WHERE {" +
                    "GRAPH ?g { " +
                    "?term a ?type ; " +
                    "      ?hasLabel ?label . " +
                    "FILTER CONTAINS(LCASE(?label), LCASE(?searchString)) ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    "} ORDER BY ?label", Term.class)
                                             .setParameter("type", typeUri)
                                             .setParameter("hasLabel", LABEL_PROP)
                                             .setParameter("inVocabulary", URI.create(
                                                     cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                             .setParameter("vocabulary", vocabularyIri)
                                             .setParameter("g",
                                                     persistenceUtils.resolveVocabularyContext(vocabularyIri))
                                             .setParameter("searchString", searchString,
                                                     config.get(ConfigParam.LANGUAGE));
            query.setDescriptor(descriptorFactory.termDescriptor(vocabularyIri));
            final List<Term> terms = executeQueryAndLoadSubTerms(query);
            terms.forEach(this::loadParentSubTerms);
            return terms;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private void loadParentSubTerms(Term parent) {
        loadSubTerms(parent);
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
    public List<Term> findAllIncludingImported(String searchString, Vocabulary vocabulary) {
        Objects.requireNonNull(searchString);
        Objects.requireNonNull(vocabulary);
        final Collection<URI> vocabularies = vocabularyDao.getTransitivelyImportedVocabularies(vocabulary);
        vocabularies.add(vocabulary.getUri());
        final List<Term> result = new ArrayList<>();
        vocabularies.forEach(v -> result.addAll(findAllImpl(searchString, v)));
        result.sort(Comparator.comparing(Asset::getLabel));
        return result;
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
            return em.createNativeQuery("ASK { GRAPH ?g {" +
                    "?term a ?type ; " +
                    "?hasLabel ?label ." +
                    "}" +
                    "?term ?inVocabulary ?vocabulary ." +
                    "FILTER (LCASE(?label) = LCASE(?searchString)) . }", Boolean.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", LABEL_PROP)
                     .setParameter("inVocabulary",
                             URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("vocabulary", vocabulary)
                     .setParameter("g", persistenceUtils.resolveVocabularyContext(vocabulary.getUri()))
                     .setParameter("searchString", label, languageTag != null ? languageTag : config.get(ConfigParam.LANGUAGE)).getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}

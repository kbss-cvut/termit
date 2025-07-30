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

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.exceptions.NoUniqueResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.ontodriver.rdf4j.util.Rdf4jUtils;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import jakarta.annotation.Nullable;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.persistence.dao.util.SparqlPatterns.bindVocabularyRelatedParameters;
import static cz.cvut.kbss.termit.persistence.dao.util.SparqlPatterns.insertLanguagePattern;
import static cz.cvut.kbss.termit.persistence.dao.util.SparqlPatterns.insertVocabularyPattern;

@Repository
public class DataDao {

    private static final URI RDFS_LABEL = URI.create(RDFS.LABEL);

    private final EntityManager em;

    private final Persistence config;

    @Autowired
    public DataDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config.getPersistence();
    }

    /**
     * Gets all properties present in the system.
     *
     * @return List of properties, ordered by label
     */
    public List<RdfsResource> findAllProperties() {
        final List<RdfsResource> result = em.createNativeQuery("SELECT ?x ?label ?comment ?type WHERE {" +
                                                                       "BIND (?property as ?type)" +
                                                                       "?x a ?type ." +
                                                                       "OPTIONAL { ?x ?has-label ?label . }" +
                                                                       "OPTIONAL { ?x ?has-comment ?comment . }" +
                                                                       "}", "RdfsResource")
                                            .setParameter("property", URI.create(RDF.PROPERTY))
                                            .setParameter("has-label", RDFS_LABEL)
                                            .setParameter("has-comment", URI.create(RDFS.COMMENT)).getResultList();
        return consolidateTranslations(result);
    }

    private static List<RdfsResource> consolidateTranslations(List<RdfsResource> queryResult) {
        final Map<URI, RdfsResource> map = new LinkedHashMap<>(queryResult.size());
        queryResult.forEach(r -> {
            if (!map.containsKey(r.getUri())) {
                map.put(r.getUri(), r);
            } else {
                final RdfsResource res = map.get(r.getUri());
                if (r.getLabel() != null) {
                    res.getLabel().getValue().putAll(r.getLabel().getValue());
                }
                if (r.getComment() != null) {
                    res.getComment().getValue().putAll(r.getComment().getValue());
                }
            }
        });
        return new ArrayList<>(map.values());
    }

    /**
     * Persists the specified resource.
     * <p>
     * This method should be used very rarely because it saves a basic RDFS resource with nothing but identifier and
     * possibly label and comment.
     *
     * @param instance The resource to persist
     */
    public void persist(RdfsResource instance) {
        Objects.requireNonNull(instance);
        try {
            em.persist(instance);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets basic metadata about a resource with the specified identifier.
     *
     * @param id Resource identifier
     * @return Wrapped matching resource or an empty {@code Optional} if no such resource exists
     */
    public Optional<RdfsResource> find(URI id) {
        Objects.requireNonNull(id);
        final List<RdfsResource> resources = consolidateTranslations(
                em.createNativeQuery("SELECT ?x ?label ?comment ?type WHERE {" +
                                             "BIND (?id AS ?x)" +
                                             "?x a ?type ." +
                                             "OPTIONAL { ?x ?has-label ?label .}" +
                                             "OPTIONAL { ?x ?has-comment ?comment . }" +
                                             "}", "RdfsResource").setParameter("id", id)
                  .setParameter("has-label", RDFS_LABEL)
                  .setParameter("has-comment", URI.create(RDFS.COMMENT)).getResultList());
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        final RdfsResource result = resources.get(0);
        result.setTypes(resources.stream().flatMap(r -> r.getTypes().stream()).collect(Collectors.toSet()));
        return Optional.of(result);
    }

    /**
     * Gets the {@link RDFS#LABEL} of a resource with the specified identifier.
     * <p>
     * Note that the label has to have language tag matching the language of the vocabulary (if available),
     * the configured persistence unit language
     * or no language tag at all (matching tag is preferred).
     *
     * @param id Resource ({@link RDFS#RESOURCE}) identifier
     * @return Matching resource identifier (if found)
     */
    public Optional<String> getLabel(URI id) {
        return getLabel(id, null);
    }

    /**
     * Gets the {@link RDFS#LABEL} of a resource with the specified identifier.
     * <p>
     * Note that the label has to have matching language tag or no language tag at all (matching tag is preferred).
     *
     * @param id Resource ({@link RDFS#RESOURCE}) identifier
     * @param language Label language, if null, the vocabulary language is used when available,
     *                 otherwise the configured persistence unit language is used instead.
     * @return Matching resource identifier (if found)
     */
    public Optional<String> getLabel(URI id, @Nullable String language) {
        Objects.requireNonNull(id);
        if (!id.isAbsolute()) {
            return Optional.of(id.toString());
        }
        String languageOptionalPattern = "";
        final boolean languageSpecified = language != null;
        if (languageSpecified) {
            // only bind parameter value to the ?labelLanguage variable if the parameter value is present
            // (COALESCE gives wrong results otherwise)
            languageOptionalPattern = "BIND (?labelLanguageVal as ?labelLanguage) .";
        } else {
            // if the language was not provided, try to find vocabulary & the entity language
            languageOptionalPattern = insertVocabularyPattern("?x") +
                                      insertLanguagePattern("?x");
        }

        TypedQuery<String> query = em.createNativeQuery("SELECT DISTINCT ?strippedLabel WHERE {" +
                                        "{?x ?has-label ?label .}" +
                                        "UNION" +
                                        "{?x ?has-title ?label .}" +
                                        "BIND (str(?label) as ?strippedLabel )." +
                                        languageOptionalPattern +
                                        "BIND (?instanceLanguageVal as ?instanceLanguage) ." +
                                        "BIND (COALESCE(" +
                                        "   ?labelLanguage," + // requested language
                                        "   ?language," + // resolved vocabulary language
                                        "   ?instanceLanguage) AS ?labelLanguage) ." +
                                        "FILTER (LANGMATCHES(LANG(?label), ?labelLanguage) || lang(?label) = \"\") }",
                                String.class)
                             .setParameter("x", id).setParameter("has-label", RDFS_LABEL)
                             .setParameter("has-title", URI.create(DC.Terms.TITLE))
                             .setParameter("instanceLanguageVal", config.getLanguage());
        if (languageSpecified) {
            query.setParameter("labelLanguageVal", language, null);
        } else {
            query.setParameter("hasLanguage", URI.create(DC.Terms.LANGUAGE));
            bindVocabularyRelatedParameters(query);
        }
        try {
            return Optional.of(query.getSingleResult());
        } catch (NoResultException | NoUniqueResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Exports the specified repository contexts as Turtle.
     *
     * @param contexts The contexts to export, possibly empty (in which case the default context is exported)
     * @return Resource containing the exported data in Turtle
     */
    public TypeAwareResource exportDataAsTurtle(URI... contexts) {
        final org.eclipse.rdf4j.repository.Repository repo = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        try (final RepositoryConnection con = repo.getConnection()) {
            final ValueFactory vf = con.getValueFactory();
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            RDFHandler writer = Rio.createWriter(RDFFormat.TURTLE, bos);
            con.export(writer,
                       Arrays.stream(contexts).map(u -> vf.createIRI(u.toString())).toArray(Resource[]::new));
            return new TypeAwareByteArrayResource(bos.toByteArray(), ExportFormat.TURTLE.getMediaType(),
                                                  ExportFormat.TURTLE.getFileExtension());
        }
    }

    /**
     * Inserts the specified raw data into the repository.
     * <p>
     * This method allows bypassing the JOPA-based persistence layer and thus should be used very carefully and
     * sparsely.
     *
     * @param data Data to insert
     */
    public void insertRawData(Collection<Quad> data) {
        Objects.requireNonNull(data);
        final org.eclipse.rdf4j.repository.Repository repo = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        try (final RepositoryConnection con = repo.getConnection()) {
            final ValueFactory vf = con.getValueFactory();
            data.forEach(quad -> {
                Value v = quad.object() instanceof URI ? vf.createIRI(quad.object().toString()) :
                          Rdf4jUtils.createLiteral(quad.object(), config.getLanguage(), vf);

                con.add(vf.createIRI(quad.subject().toString()), vf.createIRI(quad.predicate().toString()), v,
                        quad.context() != null ? vf.createIRI(quad.context().toString()) : null);
            });
        }
    }
}

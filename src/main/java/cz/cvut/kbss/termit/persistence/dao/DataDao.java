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
import cz.cvut.kbss.jopa.exceptions.NoUniqueResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants.Turtle;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.Rio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return em.createNativeQuery("SELECT ?x ?label ?comment ?type WHERE {" +
                "BIND (?property as ?type)" +
                "?x a ?type ." +
                "OPTIONAL { ?x ?has-label ?label . }" +
                "OPTIONAL { ?x ?has-comment ?comment . }" +
                "}", "RdfsResource")
                 .setParameter("property", URI.create(RDF.PROPERTY))
                 .setParameter("has-label", RDFS_LABEL)
                 .setParameter("has-comment", URI.create(RDFS.COMMENT)).getResultList();
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
        final List<RdfsResource> resources = em.createNativeQuery("SELECT ?x ?label ?comment ?type WHERE {" +
                "BIND (?id AS ?x)" +
                "?x a ?type ." +
                "OPTIONAL { ?x ?has-label ?label .}" +
                "OPTIONAL { ?x ?has-comment ?comment . }" +
                "}", "RdfsResource").setParameter("id", id)
                                               .setParameter("has-label", RDFS_LABEL)
                                               .setParameter("has-comment", URI.create(RDFS.COMMENT)).getResultList();
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
     * Note that the label has to have matching language tag or no language tag at all (matching tag is preferred).
     *
     * @param id Resource ({@link RDFS#RESOURCE}) identifier
     * @return Matching resource identifier (if found)
     */
    public Optional<String> getLabel(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.of(em.createNativeQuery("SELECT DISTINCT ?strippedLabel WHERE {" +
                    "{?x ?has-label ?label .}" +
                    "UNION" +
                    "{?x ?has-title ?label .}" +
                    "BIND (str(?label) as ?strippedLabel )." +
                    "FILTER (LANGMATCHES(LANG(?label), ?tag) || lang(?label) = \"\") }", String.class)
                                 .setParameter("x", id).setParameter("has-label", RDFS_LABEL)
                                 .setParameter("has-title", URI.create(DC.Terms.TITLE))
                                 .setParameter("tag", config.getLanguage(), null).getSingleResult());
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
            return new TypeAwareByteArrayResource(bos.toByteArray(), Turtle.MEDIA_TYPE, Turtle.FILE_EXTENSION);
        }
    }
}

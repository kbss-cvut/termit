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
import cz.cvut.kbss.jopa.vocabulary.OWL;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DataDaoTest extends BaseDaoTestRunner {

    private static final String FIRST_NAME_LABEL = "First name";

    @Autowired
    private EntityManager em;

    @Autowired
    private DataDao sut;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findAllPropertiesGetsPropertiesFromRepository() {
        generateProperties();
        final List<RdfsResource> result = sut.findAllProperties();
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(r -> r.getUri().equals(URI.create(Vocabulary.s_p_ma_krestni_jmeno))));
        assertTrue(result.stream().anyMatch(r -> r.getUri().equals(URI.create(Vocabulary.s_p_ma_prijmeni))));
        assertTrue(result.stream().anyMatch(r -> r.getUri().equals(URI.create(Vocabulary.s_p_ma_uzivatelske_jmeno))));
    }

    private void generateProperties() {
        // Here we are simulating schema presence in the repository
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(OWL.DATATYPE_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(OWL.OBJECT_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(OWL.ANNOTATION_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDF.TYPE,
                        vf.createIRI(OWL.DATATYPE_PROPERTY));
                connection.add(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDFS.LABEL,
                        vf.createLiteral(FIRST_NAME_LABEL));
                connection.add(vf.createIRI(Vocabulary.s_p_ma_prijmeni), RDF.TYPE, vf.createIRI(OWL.DATATYPE_PROPERTY));
                connection.add(vf.createIRI(Vocabulary.s_p_ma_uzivatelske_jmeno), RDF.TYPE,
                        vf.createIRI(OWL.DATATYPE_PROPERTY));
                connection.commit();
            }
        });
    }

    @Test
    void findReturnsMatchingResource() {
        generateProperties();
        final Optional<RdfsResource> result = sut.find(URI.create(Vocabulary.s_p_ma_krestni_jmeno));
        assertTrue(result.isPresent());
        assertEquals(Vocabulary.s_p_ma_krestni_jmeno, result.get().getUri().toString());
        assertEquals(FIRST_NAME_LABEL, result.get().getLabel());
    }

    @Test
    void findReturnsEmptyOptionalWhenNoMatchingResourceIsFound() {
        generateProperties();
        final Optional<RdfsResource> result = sut.find(URI.create(DC.Terms.MEDIA_TYPE));
        assertFalse(result.isPresent());
    }

    @Test
    void getLabelReturnsLabelWithMatchingLanguageOfSpecifiedIdentifier() {
        enableRdfsInference(em);    // skos:prefLabel is a subPropertyOf rdfs:label
        final Term term = Generator.generateTermWithId();
        transactional(() -> em.persist(term));

        final Optional<String> result = sut.getLabel(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term.getPrimaryLabel(), result.get());
    }

    @Test
    void getLabelReturnsLabelWithoutLanguageTagWhenMatchingLanguageTagDoesNotExist() {
        enableRdfsInference(em);    // skos:prefLabel is a subPropertyOf rdfs:label
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(term.getUri().toString()), RDF.TYPE, vf.createIRI(Vocabulary.s_c_term));
                connection.add(vf.createIRI(term.getUri().toString()), SKOS.PREF_LABEL,
                        vf.createLiteral(term.getPrimaryLabel()));
                connection.commit();
            }
        });

        final Optional<String> result = sut.getLabel(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term.getPrimaryLabel(), result.get());
    }

    @Test
    void getLabelReturnsEmptyOptionalForIdentifierWithoutLabel() {
        assertFalse(sut.getLabel(Generator.generateUri()).isPresent());
    }

    @Test
    void getLabelReturnsStringArgumentIfItIsNotAbsoluteUri() {
        final String value = "test";
        assertEquals(Optional.of(value), sut.getLabel(URI.create(value)));
    }

    @Test
    void getLabelReturnsEmptyOptionalForIdentifierWithMultipleLabels() {
        enableRdfsInference(em);    // skos:prefLabel is a subPropertyOf rdfs:label
        final Term term = Generator.generateTermWithId();
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(term.getUri().toString()), RDF.TYPE, vf.createIRI(Vocabulary.s_c_term));
                connection.add(vf.createIRI(term.getUri().toString()), SKOS.PREF_LABEL,
                        vf.createLiteral(term.getPrimaryLabel()));
                connection.add(vf.createIRI(term.getUri().toString()), SKOS.PREF_LABEL,
                        vf.createLiteral("Another label"));
                connection.commit();
            }
        });

        final Optional<String> result = sut.getLabel(term.getUri());
        assertFalse(result.isPresent());
    }

    @Test
    void persistSavesSpecifiedResource() {
        final RdfsResource resource =
                new RdfsResource(URI.create(RDFS.LABEL.toString()), "Label", "Label specification",
                        RDF.PROPERTY.toString());
        transactional(() -> sut.persist(resource));

        final RdfsResource result = em.find(RdfsResource.class, resource.getUri());
        assertNotNull(result);
        assertEquals(resource.getLabel(), result.getLabel());
        assertEquals(resource.getTypes(), result.getTypes());
    }

    @Test
    void exportDataToTurtleReturnsResourceRepresentingTurtleData() {
        generateProperties();
        transactional(() -> {
            final TypeAwareResource result = sut.exportDataAsTurtle();
            assertNotNull(result);
            assertTrue(result.getMediaType().isPresent());
            assertEquals(ExportFormat.TURTLE.getMediaType(), result.getMediaType().get());
            assertTrue(result.getFileExtension().isPresent());
            assertEquals(ExportFormat.TURTLE.getFileExtension(), result.getFileExtension().get());
        });
    }

    @Test
    void exportDataToTurtleExportsDefaultContextWhenNoArgumentsAreProvided() {
        generateProperties();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        transactional(() -> {
            final TypeAwareResource result = sut.exportDataAsTurtle();
            final Model model = parseExportToModel(result);
            assertAll(() -> assertTrue(model.contains(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDFS.LABEL,
                    vf.createLiteral(FIRST_NAME_LABEL))),
                    () -> assertTrue(model.contains(vf.createIRI(Vocabulary.s_p_ma_prijmeni), RDF.TYPE,
                            vf.createIRI(OWL.DATATYPE_PROPERTY))),
                    () -> assertTrue(model.contains(vf.createIRI(Vocabulary.s_p_ma_uzivatelske_jmeno), RDF.TYPE,
                            vf.createIRI(OWL.DATATYPE_PROPERTY))));
        });
    }

    @Test
    void exportDataToTurtleExportsContextWhenProvided() {
        final URI context = Generator.generateUri();
        generateProperties();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(Vocabulary.s_c_term), RDFS.LABEL, vf.createLiteral("Term"),
                        vf.createIRI(context.toString()));
                connection.commit();
            }
        });
        transactional(() -> {
            final TypeAwareResource result = sut.exportDataAsTurtle(context);
            Model model = parseExportToModel(result);
            assertTrue(model.contains(vf.createIRI(Vocabulary.s_c_term), RDFS.LABEL, vf.createLiteral("Term")));
            assertFalse(model.contains(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), null, null));
        });
    }

    private static Model parseExportToModel(TypeAwareResource result) {
        final RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
        Model model = new LinkedHashModel();
        parser.setRDFHandler(new StatementCollector(model));
        try {
            parser.parse(result.getInputStream(), "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return model;
    }
}

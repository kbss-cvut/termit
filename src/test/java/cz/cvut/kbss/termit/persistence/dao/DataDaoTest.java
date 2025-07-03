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
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.OWL;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
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

import static cz.cvut.kbss.termit.environment.Environment.getPrimaryLabel;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataDaoTest extends BaseDaoTestRunner {

    private static final String FIRST_NAME_LABEL = "First name";

    @Autowired
    private EntityManager em;

    @Autowired
    private DataDao sut;

    private cz.cvut.kbss.termit.model.Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
        vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary));
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
                connection.begin();
                connection.clear();
                connection.add(vf.createIRI(OWL.DATATYPE_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(OWL.OBJECT_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(OWL.ANNOTATION_PROPERTY), RDFS.SUBCLASSOF, RDF.PROPERTY);
                connection.add(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDF.TYPE,
                               vf.createIRI(OWL.DATATYPE_PROPERTY));
                connection.add(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDFS.LABEL,
                               vf.createLiteral(FIRST_NAME_LABEL, Environment.LANGUAGE));
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
        assertEquals(MultilingualString.create(FIRST_NAME_LABEL, Environment.LANGUAGE), result.get().getLabel());
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
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(term));

        final Optional<String> result = sut.getLabel(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(getPrimaryLabel(term), result.get());
    }

    @Test
    void getLabelReturnsLabelWithoutLanguageTagWhenMatchingLanguageTagDoesNotExist() {
        enableRdfsInference(em);    // skos:prefLabel is a subPropertyOf rdfs:label
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(term.getUri().toString()), RDF.TYPE, SKOS.CONCEPT);
                connection.add(vf.createIRI(term.getUri().toString()), SKOS.PREF_LABEL,
                        vf.createLiteral(getPrimaryLabel(term)));
                connection.commit();
            }
        });

        final Optional<String> result = sut.getLabel(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(getPrimaryLabel(term), result.get());
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
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(term.getUri().toString()), RDF.TYPE, SKOS.CONCEPT);
                connection.add(vf.createIRI(term.getUri().toString()), SKOS.PREF_LABEL,
                        vf.createLiteral(getPrimaryLabel(term)));
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
                new RdfsResource(URI.create(RDFS.LABEL.toString()),
                                 new LangString("Label", Environment.LANGUAGE),
                                 new LangString("Label specification", Environment.LANGUAGE), RDF.PROPERTY.toString());
        transactional(() -> sut.persist(resource));

        final RdfsResource result = em.find(RdfsResource.class, resource.getUri());
        assertNotNull(result);
        assertEquals(resource.getLabel(), result.getLabel());
        assertEquals(resource.getTypes(), result.getTypes());
    }

    @Test
    void exportDataToTurtleReturnsResourceRepresentingTurtleData() {
        generateProperties();
        readOnlyTransactional(() -> {
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
        readOnlyTransactional(() -> {
            final TypeAwareResource result = sut.exportDataAsTurtle();
            final Model model = parseExportToModel(result);
            assertAll(() -> assertTrue(model.contains(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDFS.LABEL,
                                                      vf.createLiteral(FIRST_NAME_LABEL, Environment.LANGUAGE))),
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
                connection.add(SKOS.CONCEPT, RDFS.LABEL, vf.createLiteral("Term"),
                               vf.createIRI(context.toString()));
                connection.commit();
            }
        });
        readOnlyTransactional(() -> {
            final TypeAwareResource result = sut.exportDataAsTurtle(context);
            Model model = parseExportToModel(result);
            assertTrue(model.contains(SKOS.CONCEPT, RDFS.LABEL, vf.createLiteral("Term")));
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

    @Test
    void findAllPropertiesConsolidatesMultipleTranslationsOfPropertyLabelIntoOneObject() {
        generateProperties();
        generateLabelTranslations();
        final List<RdfsResource> result = sut.findAllProperties();
        assertFalse(result.isEmpty());
        final Optional<RdfsResource> firstName = result.stream().filter(r -> r.getUri().equals(URI.create(
                Vocabulary.s_p_ma_krestni_jmeno))).findAny();
        assertTrue(firstName.isPresent());
        assertEquals(2, firstName.get().getLabel().getLanguages().size());
    }

    private void generateLabelTranslations() {
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            final ValueFactory vf = repo.getValueFactory();
            try (final RepositoryConnection connection = repo.getConnection()) {
                connection.add(vf.createIRI(Vocabulary.s_p_ma_krestni_jmeno), RDFS.LABEL,
                               vf.createLiteral("Má křestní jméno", "cs"));
            }
        });
    }

    @Test
    void insertDataInsertsSpecifiedQuadsIntoRepository() {
        final URI context = Generator.generateUri();
        final URI termOne = Generator.generateUri();
        final URI termTwo = Generator.generateUri();
        final List<Quad> quads = List.of(
                new Quad(termOne, URI.create(RDF.TYPE.stringValue()), URI.create(SKOS.CONCEPT.stringValue()), context),
                new Quad(termTwo, URI.create(RDF.TYPE.stringValue()), URI.create(SKOS.CONCEPT.stringValue()), context),
                new Quad(termOne, URI.create(SKOS.RELATED.stringValue()), termTwo, context)
        );

        transactional(() -> sut.insertRawData(quads));
        readOnlyTransactional(() -> assertTrue(
                em.createNativeQuery("ASK WHERE { GRAPH ?ctx { ?x a ?type . ?y a ?type . ?x ?related ?y . } }",
                                     Boolean.class)
                  .setParameter("x", termOne)
                  .setParameter("y", termTwo)
                  .setParameter("ctx", context)
                  .setParameter("related", URI.create(SKOS.RELATED.stringValue()))
                  .setParameter("type", URI.create(SKOS.CONCEPT.stringValue())).getSingleResult()));
    }
}

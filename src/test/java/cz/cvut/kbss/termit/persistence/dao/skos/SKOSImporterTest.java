package cz.cvut.kbss.termit.persistence.dao.skos;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SKOSImporterTest extends BaseDaoTestRunner {

    private static final String VOCABULARY_IRI_S = "http://onto.fel.cvut.cz/ontologies/application/termit";
    private static final URI VOCABULARY_IRI = URI.create(VOCABULARY_IRI_S);
    private static final String GLOSSARY_IRI = "http://onto.fel.cvut.cz/ontologies/application/termit/glosář";

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private ApplicationContext context;

    private final Consumer<cz.cvut.kbss.termit.model.Vocabulary> persister = (cz.cvut.kbss.termit.model.Vocabulary v) -> vocabularyDao.persist(v);

    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> em.persist(author));
        transactional(() -> {
            final cz.cvut.kbss.termit.model.Vocabulary v = generateVocabulary();
            v.setUri(VOCABULARY_IRI);
            em.persist(v, descriptorFactory.vocabularyDescriptor(VOCABULARY_IRI));
        });
    }

    @Test
    void importVocabularyImportsGlossaryFromSpecifiedStream() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertTrue(conn.hasStatement(vf.createIRI(Vocabulary.s_c_uzivatel_termitu), RDF.TYPE, SKOS.CONCEPT,
                    false));
                assertTrue(
                    conn.hasStatement(vf.createIRI(Vocabulary.s_c_omezeny_uzivatel_termitu), RDF.TYPE, SKOS.CONCEPT,
                        false));
                assertTrue(conn.hasStatement(vf.createIRI(Vocabulary.s_c_zablokovany_uzivatel_termitu), RDF.TYPE,
                    SKOS.CONCEPT, false));
            }
        });
    }

    @Test
    void importVocabularyRenamesVocabularyIriWhenAlreadyPresent() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, null, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, null, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertTrue(conn.hasStatement(vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit-0"), RDF.TYPE,
                    vf.createIRI(Vocabulary.s_c_slovnik), false));
            }
        });
    }

    @Test
    void importVocabularyRenamesTermIriUponRenamingVocabularyIri() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, null, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, null, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                assertTrue(conn.hasStatement(vf.createIRI("http://onto.fel.cvut.cz/ontologies/application/termit-0/pojem/zablokovan\u00fd-u\u017eivatel-termitu"), RDF.TYPE,
                    SKOS.CONCEPT, false));
            }
        });
    }

    @Test
    void importThrowsIllegalArgumentExceptionWhenNoStreamIsProvided() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            assertThrows(IllegalArgumentException.class, () -> sut.importVocabulary(false, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister));
        });
    }

    @Test
    void importThrowsIllegalArgumentExceptionWhenVocabularyIriIsGivenButDoesNotMatchTheImportedData() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            assertThrows(IllegalArgumentException.class, () ->
                    sut.importVocabulary(true, URI.create(VOCABULARY_IRI_S + "-1"), Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"))
            );
        });
    }

    @Test
    void importInsertsImportedDataIntoContextBasedOnOntologyIdentifier() {
        final AtomicInteger existingStatementCountInDefault = new AtomicInteger(0);
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                existingStatementCountInDefault.set(Iterations.asList(conn.getStatements(null, null, null, false, (Resource) null)).size());
            }
        });
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
        });
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final List<Resource> contexts = Iterations.asList(conn.getContextIDs());
                assertFalse(contexts.isEmpty());
                final Optional<Resource> ctx = contexts.stream().filter(r -> r.stringValue().contains(VOCABULARY_IRI.toString()))
                    .findFirst();
                assertTrue(ctx.isPresent());
                final List<Statement> inAll = Iterations.asList(conn.getStatements(null, null, null, false));
                final List<Statement> inCtx = Iterations.asList(conn.getStatements(null, null, null, false, ctx.get()));
                assertEquals(inAll.size() - existingStatementCountInDefault.get(), inCtx.size());
            }
        });
    }

    @Test
    void importResolvesVocabularyIriForContextWhenMultipleStreamsWithGlossaryAndVocabularyAreProvided() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"),
                Environment.loadFile("data/test-vocabulary.ttl"));
        });
        transactional(() -> {
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection conn = repo.getConnection()) {
                final List<Resource> contexts = Iterations.asList(conn.getContextIDs());
                assertFalse(contexts.isEmpty());
                contexts.forEach(ctx -> assertThat(ctx.stringValue(), containsString(VOCABULARY_IRI.toString())));

            }
        });
    }

    @Test
    void importThrowsIllegalArgumentExceptionWhenTargetContextCannotBeDeterminedFromSpecifiedData() {
        final String input = "@prefix termit: <http://onto.fel.cvut.cz/ontologies/application/termit/> .\n" +
            "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
            "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" +
            "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" +
            "@prefix termit-pojem: <http://onto.fel.cvut.cz/ontologies/application/termit/pojem/> .\n" +
            "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> ." +
            "termit-pojem:zablokovaný-uživatel-termitu\n" +
            "        a       <http://www.w3.org/2004/02/skos/core#Concept> ;\n" +
            "        <http://www.w3.org/2004/02/skos/core#broader>\n" +
            "                termit-pojem:uživatel-termitu , <https://slovník.gov.cz/základní/pojem/typ-objektu> ;\n" +
            "        <http://www.w3.org/2004/02/skos/core#inScheme>\n" +
            "                termit:glosář ;\n" +
            "        <http://www.w3.org/2004/02/skos/core#prefLabel>\n" +
            "                \"Blocked TermIt user\"@en , \"Zablokovaný uživatel TermItu\"@cs .";
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            final VocabularyImportException ex = assertThrows(VocabularyImportException.class,
                () -> sut
                    .importVocabulary(false, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, new ByteArrayInputStream(input.getBytes(
                        StandardCharsets.UTF_8))));
            assertThat(ex.getMessage(), containsString("No unique skos:ConceptScheme found in the provided data."));
        });
    }

    @Test
    void importThrowsUnsupportedImportMediaTypeExceptionForUnsupportedDataType() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            assertThrows(UnsupportedImportMediaTypeException.class, () -> sut
                .importVocabulary(false, VOCABULARY_IRI, Constants.Excel.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl")));
        });
    }

    @Test
    void importReturnsVocabularyInstanceConstructedFromImportedData() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            final cz.cvut.kbss.termit.model.Vocabulary result = sut
                .importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"));
            assertNotNull(result);
            assertEquals(VOCABULARY_IRI, result.getUri());
            assertEquals("Vocabulary of system TermIt - glossary", result.getLabel());
        });
    }

    @Test
    void importGeneratesRelationshipsBetweenTermsAndGlossary() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"),
                Environment.loadFile("data/test-vocabulary.ttl"));
        });
        transactional(() -> {
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                final List<Resource> terms = Iterations.stream(conn.getStatements(null, RDF.TYPE, SKOS.CONCEPT))
                    .map(Statement::getSubject).collect(Collectors.toList());
                assertFalse(terms.isEmpty());
                terms.forEach(t -> assertTrue(conn.getStatements(t, SKOS.IN_SCHEME,
                    vf.createIRI(GLOSSARY_IRI)).hasNext()));
            }
        });
    }

    @Test
    void importGeneratesTopConceptAssertions() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary.ttl"),
                Environment.loadFile("data/test-vocabulary.ttl"));
        });
        transactional(() -> {
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                final List<Value> terms = Iterations.stream(conn.getStatements(null, SKOS.HAS_TOP_CONCEPT, null))
                    .map(Statement::getObject).collect(Collectors.toList());
                assertEquals(1, terms.size());
                assertThat(terms, hasItem(vf
                    .createIRI("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/uživatel-termitu")));
            }
        });
    }

    @Test
    void importGeneratesTopConceptAssertionsForGlossaryUsingNarrowerProperty() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary-narrower.ttl"),
                Environment.loadFile("data/test-vocabulary.ttl"));
        });
        transactional(() -> {
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                final List<Value> terms = Iterations.stream(conn.getStatements(null, SKOS.HAS_TOP_CONCEPT, null))
                    .map(Statement::getObject).collect(Collectors.toList());
                assertEquals(1, terms.size());
                assertThat(terms, hasItem(vf
                    .createIRI("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/uživatel-termitu")));
            }
        });
    }

    @Test
    void importFailsIfAnEmptyLanguageTagIsProvidedForMultilingualProperties() {
        transactional(() -> {
            final SKOSImporter sut = context.getBean(SKOSImporter.class);
            assertThrows(IllegalArgumentException.class, () ->
                    sut.importVocabulary(true, VOCABULARY_IRI, Constants.Turtle.MEDIA_TYPE, persister, Environment.loadFile("data/test-glossary-narrower.ttl"),
                            Environment.loadFile("data/test-glossary-with-definition-with-empty-language-tag.ttl")));
        });
    }
}

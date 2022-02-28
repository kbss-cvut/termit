package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TermAssignmentDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermAssignmentDao sut;

    private Resource resource;

    @BeforeEach
    void setUp() {
        this.resource = new Resource();
        resource.setUri(Generator.generateUri());
        resource.setLabel("Metropolitan Plan");
        final User author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        transactional(() -> {
            em.persist(author);
            em.persist(resource);
        });
    }

    private Document getDocument(final File... files) {
        final Document document = Generator.generateDocumentWithId();
        document.setLabel("Doc");
        Arrays.stream(files).forEach(document::addFile);
        return document;
    }

    @Test
    void getAssignmentsInfoByTermRetrievesAggregateTermOccurrences() {
        final Term term = Generator.generateTermWithId();
        final File fOne = Generator.generateFileWithId("testOne.html");
        final File fTwo = Generator.generateFileWithId("testTwo.html");
        final Document document = getDocument(fOne, fTwo);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(fOne);
            em.persist(fTwo);
            em.persist(document);
        });
        final List<TermOccurrence> occurrencesOne = generateTermOccurrences(term, fOne, false);
        final List<TermOccurrence> occurrencesTwo = generateTermOccurrences(term, fTwo, true);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(2, result.size());
        for (TermAssignments tai : result) {
            assertTrue(tai instanceof TermOccurrences);
            final TermOccurrences toi = (TermOccurrences) tai;
            if (toi.hasType(Vocabulary.s_c_navrzeny_vyskyt_termu)) {
                assertEquals(occurrencesTwo.size(), toi.getCount().intValue());
            } else {
                assertEquals(occurrencesOne.size(), toi.getCount().intValue());
            }
        }
    }

    @Test
    void getAssignmentsInfoByTermRetrievesTermOccurrencesAggregatedByDocument() {
        final Term term = Generator.generateTermWithId();
        final File fOne = Generator.generateFileWithId("testOne.html");
        final File fTwo = Generator.generateFileWithId("testTwo.html");
        final Document document = getDocument(fOne, fTwo);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(fOne);
            em.persist(fTwo);
            em.persist(document);
        });
        final List<TermOccurrence> occurrencesOne = generateTermOccurrences(term, fOne, false);
        final List<TermOccurrence> occurrencesTwo = generateTermOccurrences(term, fTwo, false);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(1, result.size());
        TermAssignments tai = result.get(0);
        assertTrue(tai instanceof TermOccurrences);
        final TermOccurrences toi = (TermOccurrences) tai;
        assertEquals(occurrencesOne.size() + occurrencesTwo.size(), toi.getCount().intValue());
    }

    @Test
    void getAssignmentsInfoByTermRetrievesSeparateInstancesForSuggestedAndAssertedOccurrencesOfSameTerm() {
        final Term term = Generator.generateTermWithId();
        final File file = Generator.generateFileWithId("testOne.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
        });
        generateTermOccurrences(term, file, false);
        generateTermOccurrences(term, file, true);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(2, result.size());
        result.forEach(tai -> assertEquals(term.getUri(), tai.getTerm()));
    }

    private List<TermOccurrence> generateTermOccurrences(Term term, Asset<?> target, boolean suggested) {
        final List<TermOccurrence> occurrences = IntStream.range(0, Generator.randomInt(5, 10))
                                                          .mapToObj(i -> Generator.generateTermOccurrence(term, target, suggested))
                                                          .collect(Collectors.toList());
        transactional(() -> occurrences.forEach(to -> {
            em.persist(to);
            em.persist(to.getTarget());
        }));
        return occurrences;
    }

    private void saveAssetLabelInOtherLanguage(Asset<?> asset) {
        assertEquals(Environment.LANGUAGE,
                em.getEntityManagerFactory().getProperties().get(JOPAPersistenceProperties.LANG));
        final Repository repo = em.unwrap(Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.add(vf.createStatement(vf.createIRI(asset.getUri().toString()), RDFS.LABEL,
                    vf.createLiteral("Czech label", "cs")));
        }
    }

    @Test
    void getAssignmentsInfoByTermReturnsOnlyAssignmentsForMatchingResourceLabelLanguage() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
            saveAssetLabelInOtherLanguage(file);
        });
        generateTermOccurrences(term, file, false);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(1, result.size());
    }

    @Test
    void getAssignmentsInfoByTermReturnsDistinguishableFileAndDefinitionalOccurrences() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final Term targetTerm = Generator.generateTermWithId();
        targetTerm.setVocabulary(term.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(targetTerm);
            em.persist(file);
            em.persist(document);
        });
        final List<TermOccurrence> fileOccurrences = generateTermOccurrences(term, file, true);
        final List<TermOccurrence> defOccurrences = generateTermOccurrences(term, targetTerm, true);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(2, result.size());
        for (TermAssignments a : result) {
            assertThat(a, instanceOf(TermOccurrences.class));
            if (a.getTypes().contains(Vocabulary.s_c_souborovy_vyskyt_termu)) {
                assertEquals(fileOccurrences.size(), ((TermOccurrences) a).getCount().intValue());
            } else {
                assertThat(a.getTypes(), hasItem(Vocabulary.s_c_definicni_vyskyt_termu));
                assertEquals(defOccurrences.size(), ((TermOccurrences) a).getCount().intValue());
            }
        }
    }

    @Test
    void loadUnusedTermsInVocabularyWorks() {
        cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term1 = Generator.generateTermWithId();
        term1.setVocabulary(vocabulary.getUri());
        final Term term2 = Generator.generateTermWithId();
        term2.setVocabulary(term1.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument(file);
        generateTermOccurrences(term2, file, false);

        transactional(() -> {
            enableRdfsInference(em);
            em.persist(vocabulary);
            em.persist(file);
            em.persist(document);
            em.persist(term1);
            em.persist(term2);
        });

        final List<URI> result = sut.getUnusedTermsInVocabulary(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term1.getUri(), result.get(0));
    }
}

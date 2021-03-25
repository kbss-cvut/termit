package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.dto.assignment.ResourceTermOccurrences;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.*;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.selector.XPathSelector;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

import java.util.Arrays;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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
        Arrays.stream(files).forEach(f -> document.addFile(f));
        return document;
    }

    @Test
    void getAssignmentsInfoByTermRetrievesAssignmentsOfSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(document);
            em.persist(file);
        });
        generateAssignment(term, resource, false);
        generateAssignment(term, file, false);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(2, result.size());
        for (TermAssignments tai : result) {
            if (tai.getResource().equals(resource.getUri())) {
                assertEquals(resource.getLabel(), tai.getResourceLabel());
            } else {
                assertEquals(document.getLabel(), tai.getResourceLabel());
            }
            assertEquals(term.getUri(), tai.getTerm());
        }
    }

    private void generateAssignment(Term term, Resource resource, boolean suggested) {
        final Target target = new Target();
        target.setSource(resource.getUri());
        final TermAssignment ta = new TermAssignment();
        ta.setTerm(term.getUri());
        ta.setTarget(target);
        if (suggested) {
            ta.addType(Vocabulary.s_c_navrzene_prirazeni_termu);
        }
        transactional(() -> {
            em.persist(ta);
            em.persist(ta.getTarget());
        });
    }

    @Test
    void getAssignmentsInfoByTermRetrievesAggregateTermOccurrences() {
        final Term term = Generator.generateTermWithId();
        final File fOne = Generator.generateFileWithId("testOne.html");
        final File fTwo = Generator.generateFileWithId("testTwo.html");
        final Document document = getDocument( fOne, fTwo );
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
        final Document document = getDocument( fOne, fTwo );
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
        final Document document = getDocument( file );
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

    private List<TermAssignment> generateAssignmentsForTarget(Term term, Target target) {
        final List<TermAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final TermAssignment ta = new TermAssignment();
            ta.setTerm(term.getUri());
            ta.setTarget(target);
            assignments.add(ta);
        }
        transactional(() -> assignments.forEach(em::persist));
        return assignments;
    }

    @Test
    void findByTargetReturnsCorrectAssignments() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> em.persist(term));

        final Target target = new Target();
        target.setSource(resource.getUri());
        transactional(() -> em.persist(target));

        final List<TermAssignment> expected = generateAssignmentsForTarget(term, target);
        final List<TermAssignment> result = sut.findByTarget(target);
        assertEquals(expected.size(), result.size());
        for (TermAssignment ta : result) {
            assertTrue(expected.stream().anyMatch(assignment -> assignment.getUri().equals(ta.getUri())));
        }
    }

    @Test
    void findAllByTargetReturnsNoAssignmentsForTargetWithoutAssignment() {
        final Target target = new Target();
        target.setSource(resource.getUri());
        target.setUri(Generator.generateUri());
        transactional(() -> em.persist(target));

        assertTrue(sut.findByTarget(target).isEmpty());
    }

    @Test
    void findAllByResourceReturnsAllAssignmentsAndOccurrencesRelatedToSpecifiedResource() {
        final Term term = Generator.generateTermWithId();
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );

        final Target target = new Target();
        target.setSource(file.getUri());
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(target);
            em.persist(file);
            em.persist(document);
        });
        final List<TermAssignment> assignments = generateAssignmentsForTarget(term, target);
        final List<TermOccurrence> occurrences = generateTermOccurrences(term, file, false);

        final List<TermAssignment> result = sut.findAll(file);
        assertEquals(assignments.size() + occurrences.size(), result.size());
    }

    private List<TermOccurrence> generateTermOccurrences(Term term, Asset<?> target, boolean suggested) {
        final List<TermOccurrence> occurrences = IntStream.range(0, Generator.randomInt(5, 10))
                .mapToObj(i -> createOccurrence(term, target, suggested))
                .collect(Collectors.toList());
        transactional(() -> occurrences.forEach(to -> {
            em.persist(to);
            em.persist(to.getTarget());
        }));
        return occurrences;
    }

    private TermOccurrence createOccurrence(Term term, Asset<?> target, boolean suggested) {
        final TermOccurrence occurrence;
        if (target instanceof File) {
            occurrence = new TermFileOccurrence(term.getUri(), new FileOccurrenceTarget((File) target));
        } else {
            assert target instanceof Term;
            occurrence = new TermDefinitionalOccurrence(term.getUri(), new DefinitionalOccurrenceTarget((Term) target));
        }
        if (suggested) {
            occurrence.addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
        }
        // Dummy selector
        occurrence.getTarget().setSelectors(Collections.singleton(new XPathSelector("//div")));
        return occurrence;
    }

    @Test
    void getAssignmentsInfoByResourceRetrievesInformationAboutAssignmentsAndOccurrencesForSpecifiedResource() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );

        generateAssignment(term, file, false);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
        });
        final List<TermOccurrence> occurrences = generateTermOccurrences(term, file, false);

        final List<ResourceTermAssignments> result = sut.getAssignmentInfo(document);
        assertEquals(2, result.size());
        result.forEach(rta -> {
            assertEquals(document.getUri(), rta.getResource());
            assertEquals(term.getUri(), rta.getTerm());
            assertEquals(term.getPrimaryLabel(), rta.getTermLabel());
        });
        final Optional<ResourceTermAssignments> occ = result.stream()
                .filter(ResourceTermOccurrences.class::isInstance)
                .findAny();
        assertTrue(occ.isPresent());
        assertEquals(occurrences.size(), ((ResourceTermOccurrences) occ.get()).getCount().intValue());
    }

    @Test
    void getAssignmentsInfoByResourceRetrievesInfoAboutSuggestedAssignmentsAndOccurrencesForSpecifiedResource() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );

        generateAssignment(term, file, true);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
        });
        final List<TermOccurrence> occurrences = generateTermOccurrences(term, file, true);

        final List<ResourceTermAssignments> result = sut.getAssignmentInfo(document);
        assertEquals(2, result.size());
        result.forEach(rta -> {
            assertEquals(document.getUri(), rta.getResource());
            assertEquals(term.getUri(), rta.getTerm());
            assertEquals(term.getPrimaryLabel(), rta.getTermLabel());
            assertThat(rta.getTypes(), either(hasItem(Vocabulary.s_c_navrzene_prirazeni_termu))
                    .or(hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
        });
        final Optional<ResourceTermAssignments> occ = result.stream()
                .filter(rta -> rta instanceof ResourceTermOccurrences)
                .findAny();
        assertTrue(occ.isPresent());
        assertEquals(occurrences.size(), ((ResourceTermOccurrences) occ.get()).getCount().intValue());
    }

    @Test
    void getAssignmentsInfoByResourceRetrievesAssignmentsWhenNoOccurrencesAreFoundForSpecifiedResource() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());

        generateAssignment(term, resource, true);
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
        });

        final List<ResourceTermAssignments> result = sut.getAssignmentInfo(resource);
        assertEquals(1, result.size());
        assertEquals(resource.getUri(), result.get(0).getResource());
        assertEquals(term.getUri(), result.get(0).getTerm());
        assertEquals(term.getPrimaryLabel(), result.get(0).getTermLabel());
    }

    @Test
    void getAssignmentsInfoByResourceRetrievesSeparateInstancesForSuggestedAndAssertedOccurrencesOfSameTerm() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
        });
        final List<TermOccurrence> suggested = generateTermOccurrences(term, file, true);
        final List<TermOccurrence> asserted = generateTermOccurrences(term, file, false);

        final List<ResourceTermAssignments> result = sut.getAssignmentInfo(document);
        assertEquals(2, result.size());
        for (ResourceTermAssignments rta : result) {
            if (rta.getTypes().contains(Vocabulary.s_c_navrzeny_vyskyt_termu)) {
                assertEquals(suggested.size(), ((ResourceTermOccurrences) rta).getCount().intValue());
            } else {
                assertEquals(asserted.size(), ((ResourceTermOccurrences) rta).getCount().intValue());
            }
            assertEquals(term.getUri(), rta.getTerm());
            assertEquals(term.getPrimaryLabel(), rta.getTermLabel());
            assertEquals(document.getUri(), rta.getResource());
            assertEquals(term.getVocabulary(), rta.getVocabulary());
        }
    }

    @Test
    void getAssignmentsInfoByResourceReturnsOnlyTermsWithLabelMatchingSystemLanguage() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
            saveAssetLabelInOtherLanguage(term);
        });
        generateTermOccurrences(term, file, false);
        generateAssignment(term, file, false);

        final List<ResourceTermAssignments> result = sut.getAssignmentInfo(document);
        // One assignment and one occurrence
        assertEquals(2, result.size());
        result.forEach(rta -> assertEquals(term.getPrimaryLabel(), rta.getTermLabel()));
    }

    private void saveAssetLabelInOtherLanguage(Asset<?> asset) {
        assertEquals(Constants.DEFAULT_LANGUAGE,
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
        final Document document = getDocument( file );
        transactional(() -> {
            enableRdfsInference(em);
            em.persist(term);
            em.persist(file);
            em.persist(document);
            saveAssetLabelInOtherLanguage(file);
        });
        generateTermOccurrences(term, file, false);
        generateAssignment(term, file, false);

        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        // One assignment and one occurrence
        assertEquals(2, result.size());
    }

    @Test
    void getAssignmentsInfoByTermReturnsDistinguishableFileAndDefinitionalOccurrences() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final Term targetTerm = Generator.generateTermWithId();
        targetTerm.setVocabulary(term.getVocabulary());
        final File file = Generator.generateFileWithId("test.html");
        final Document document = getDocument( file );
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
        final Resource resource = Generator.generateResourceWithId();
        final Target target = Generator.generateTargetWithId();
        final TermAssignment assignment = Generator.generateTermAssignmentWithId();
        assignment.setTerm(term2.getUri());
        assignment.setTarget(target);
        target.setSource(resource.getUri());

        transactional(() -> {
            enableRdfsInference(em);
            em.persist(vocabulary);
            em.persist(resource);
            em.persist(assignment);
            em.persist(target);
            em.persist(term1);
            em.persist(term2);
        });

        final List<URI> result = sut.getUnusedTermsInVocabulary(vocabulary);
        assertEquals(1, result.size());
        assertEquals(term1.getUri(), result.get(0));
    }
}

package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class contains tests of retrieval behavior of {@link TermRepositoryService}.
 *
 * It utilizes a once-time set up, i.e., test data are generated only once for all tests.
 */
public class TermRepositoryServiceRetrieveTest extends BaseServiceTestRunner {

    private static final String MATCHING_LABEL_BASE = "Matching ";

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermRepositoryService sut;

    private static UserAccount user;
    private static Vocabulary vocabulary;
    private static Vocabulary childVocabulary;

    private static List<Term> terms;

    private static boolean initialized = false;

    @BeforeEach
    void setUp() {
        init();
    }

    private void init() {
        if (initialized) {
            return;
        }
        user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);

        vocabulary = Generator.generateVocabularyWithId();
        childVocabulary = Generator.generateVocabularyWithId();
        childVocabulary.setImportedVocabularies(Collections.singleton(vocabulary.getUri()));
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(childVocabulary, descriptorFactory.vocabularyDescriptor(childVocabulary));
        });
        terms = generateAndPersistTerms();
        initialized = true;
    }

    private List<Term> generateAndPersistTerms() {
        final List<Term> terms = Generator.generateTermsWithIds(10);
        terms.subList(0, terms.size() / 2).forEach(t -> t.getLabel().set(Environment.LANGUAGE,
                                                                         MATCHING_LABEL_BASE + t.getLabel()
                                                                                                .get(Environment.LANGUAGE)));
        vocabulary.getGlossary().setRootTerms(terms.stream().map(Asset::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            terms.forEach(t -> {
                t.setVocabulary(vocabulary.getUri());
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            });
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        return terms;
    }

    @Test
    void findAllRootsReturnsRootTermsOnMatchingPage() {
        final List<TermDto> resultOne = sut.findAllRoots(vocabulary, PageRequest.of(0, 5), Collections.emptyList());
        final List<TermDto> resultTwo = sut.findAllRoots(vocabulary, PageRequest.of(1, 5), Collections.emptyList());

        assertEquals(5, resultOne.size());
        assertEquals(5, resultTwo.size());

        final List<TermDto> expectedDtos = termsToDtos(terms);
        resultOne.forEach(t -> {
            assertThat(expectedDtos, hasItem(t));
            assertThat(resultTwo, not(hasItem(t)));
        });
        resultTwo.forEach(t -> assertThat(expectedDtos, hasItem(t)));
    }

    @Test
    void existsInVocabularyChecksForTermWithMatchingLabel() {
        final Term term = terms.get(Generator.randomIndex(terms));

        assertTrue(sut.existsInVocabulary(term.getLabel().get(Environment.LANGUAGE), vocabulary, Environment.LANGUAGE));
    }

    @Test
    void findAllRootsIncludingImportsReturnsRootTermsOnMatchingPage() {
        final List<TermDto> resultOne = sut
                .findAllRootsIncludingImported(vocabulary, PageRequest.of(0, 5), Collections.emptyList());
        final List<TermDto> resultTwo = sut
                .findAllRootsIncludingImported(vocabulary, PageRequest.of(1, 5), Collections.emptyList());

        assertEquals(5, resultOne.size());
        assertEquals(5, resultTwo.size());

        final List<TermDto> expectedDtos = termsToDtos(terms);
        resultOne.forEach(t -> {
            assertThat(expectedDtos, hasItem(t));
            assertThat(resultTwo, not(hasItem(t)));
        });
        resultTwo.forEach(t -> assertThat(expectedDtos, hasItem(t)));
    }

    @Test
    void findTermsBySearchStringReturnsMatchingTerms() {
        final List<Term> matching = terms.stream().filter(t -> t.getLabel().get(Environment.LANGUAGE)
                                                                .startsWith(MATCHING_LABEL_BASE))
                                         .collect(Collectors.toList());

        List<TermDto> result = sut.findAll(
                MATCHING_LABEL_BASE.substring(0, MATCHING_LABEL_BASE.length() - Generator.randomInt(0, 3)), vocabulary);
        assertEquals(matching.size(), result.size());
        assertTrue(termsToDtos(matching).containsAll(result));
    }

    @Test
    void findAllIncludingImportedBySearchStringReturnsMatchingTerms() {
        final List<Term> matching = terms.stream().filter(t -> t.getLabel().get(Environment.LANGUAGE)
                                                                .startsWith(MATCHING_LABEL_BASE))
                                         .collect(Collectors.toList());
        final String searchString = MATCHING_LABEL_BASE.substring(0,
                                                                  MATCHING_LABEL_BASE.length() - Generator.randomInt(0,
                                                                                                                     3));

        List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(matching.size(), result.size());
        assertTrue(termsToDtos(matching).containsAll(result));
    }

    @Test
    void findAllWithSearchStringReturnsMatchingTerms() {
        final List<Term> matching = terms.stream().filter(t -> t.getLabel().get(Environment.LANGUAGE)
                                                                .startsWith(MATCHING_LABEL_BASE))
                                         .collect(Collectors.toList());
        final String searchString = MATCHING_LABEL_BASE.substring(0,
                                                                  MATCHING_LABEL_BASE.length() - Generator.randomInt(0,
                                                                                                                     3));

        List<TermDto> result = sut.findAll(searchString);
        assertEquals(matching.size(), result.size());
        assertTrue(termsToDtos(matching).containsAll(result));
    }
}

package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IriMigrationRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private TermService termService;

    @Autowired
    private DataRepositoryService dataService;

    @Autowired
    private IriMigrationRepositoryService sut;

    @MockitoBean
    private TextAnalysisService textAnalysisService; // no-op analysis service

    private Vocabulary vocabularyA;

    private Vocabulary vocabularyB;

    private List<Term> termsA;

    private List<Term> termsB;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        author.addType(UserRole.ADMIN.getType());
        Environment.setCurrentUser(author);
        transactional(() -> em.persist(author));

        vocabularyA = generateVocabulary();
        vocabularyB = generateVocabulary();
        termsA = generateTerms(vocabularyA, 5);
        termsB = generateTerms(vocabularyB, 5);
    }

    private Vocabulary generateVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabularyService.persist(vocabulary);
        return vocabulary;
    }

    private Term generateTerm(Vocabulary vocabulary) {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(term.getUri());
        final String namespace = Objects.requireNonNull(vocabulary.getPreferredNamespaceUri());
        term.setUri(URI.create(namespace + fragment));
        termService.persistRoot(term, vocabulary);
        return term;
    }

    private List<Term> generateTerms(Vocabulary vocabulary, int count) {
        return IntStream.range(0, count).mapToObj(i -> generateTerm(vocabulary)).toList();
    }

    private CustomAttribute generateCustomAttribute() {
        final CustomAttribute attribute = new CustomAttribute();
        attribute.setUri(Generator.generateUri());
        attribute.setLabel(
                MultilingualString.create("Custom attribute " + Generator.randomInt(), Environment.LANGUAGE));
        dataService.persistCustomAttribute(attribute);
        return attribute;
    }

    private IriMigrationPair iriMigration(URI originalIri) {
        return new IriMigrationPair(originalIri, Generator.generateUri());
    }

    @ParameterizedTest
    @EnumSource(IriMigrationType.class)
    void migrateIdentifierThrowsForNonExistingAsset(IriMigrationType type) {
        final IriMigrationPair iris = iriMigration(Generator.generateUri());
        final IriMigrationParams params = new IriMigrationParams();

        assertThrows(NotFoundException.class, () -> sut.migrateIdentifier(iris, type, params));
    }

    @Test
    void migrateIdentifierMigratesTermIdentifier() {
        final Term toMigrate = termsA.getFirst();
        final Term related = termsB.getFirst();

        related.setRelatedMatch(Set.of(toMigrate.toTermInfo()));
        termService.update(related);

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params);

        final Term migrated = termService.findRequired(iris.newIri());
        final Term unchanged = termService.findRequired(related.getUri());

        assertThrows(NotFoundException.class, () -> termService.findRequired(iris.originalIri()));

        assertEquals(toMigrate.getLabel(), migrated.getLabel());
        assertEquals(related.getLabel(), unchanged.getLabel());

        assertEquals(Set.of(migrated.toTermInfo()), unchanged.getRelatedMatch());
    }

    @Test
    void migrateIdentifierMigratesVocabularyIdentifier() {
        final Vocabulary toMigrate = vocabularyA;
        final Vocabulary related = vocabularyB;

        related.setImportedVocabularies(Set.of(toMigrate.getUri()));
        vocabularyService.update(related);

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);

        final Vocabulary migrated = vocabularyService.findRequired(iris.newIri());
        final Vocabulary unchanged = vocabularyService.findRequired(related.getUri());

        assertThrows(NotFoundException.class, () -> vocabularyService.findRequired(iris.originalIri()));

        assertEquals(toMigrate.getLabel(), migrated.getLabel());
        assertEquals(related.getLabel(), unchanged.getLabel());

        assertEquals(Set.of(migrated.getUri()), unchanged.getImportedVocabularies());
    }

    @Test
    void migrateIdentifierMigratesCustomAttributeIdentifier() {
        final CustomAttribute toMigrate = generateCustomAttribute();
        final CustomAttribute unrelated = generateCustomAttribute();

        final Set<Object> migratedValue = Set.of("Value of migrated attribute");
        final Set<Object> unchangedValue = Set.of("Value of unchanged attribute");

        final Term term = termsA.getFirst();
        term.setProperties(new HashMap<>(Map.of(
                toMigrate.getUri().toString(), migratedValue,
                unrelated.getUri().toString(), unchangedValue)));
        termService.update(term);

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.CUSTOM_ATTRIBUTE, params);

        final CustomAttribute migrated =
                dataService.findCustomAttribute(iris.newIri()).orElseThrow();
        final CustomAttribute unchanged =
                dataService.findCustomAttribute(unrelated.getUri()).orElseThrow();

        assertTrue(dataService.findCustomAttribute(iris.originalIri()).isEmpty());

        assertEquals(toMigrate.getLabel(), migrated.getLabel());
        assertEquals(unrelated.getLabel(), unchanged.getLabel());

        final Map<String, Set<Object>> properties =
                termService.findRequired(term.getUri()).getProperties();

        assertFalse(properties.containsKey(iris.originalIri().toString()));
        assertEquals(migratedValue, properties.get(iris.newIri().toString()));
        assertEquals(unchangedValue, properties.get(unrelated.getUri().toString()));
    }

    @Test
    void migrateIdentifierMigratesVocabularyGraph() {
        final Vocabulary toMigrate = vocabularyA;
        final Vocabulary related = vocabularyB;

        related.setImportedVocabularies(Set.of(toMigrate.getUri()));
        vocabularyService.update(related);

        assertTrue(askGraphExists(toMigrate.getUri()));
        assertTrue(askGraphExists(related.getUri()));

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);

        assertFalse(askGraphExists(toMigrate.getUri()), "Old vocabulary graph was not migrated!");
        assertTrue(askGraphExists(iris.newIri()), "New vocabulary graph does not exists!");
        assertTrue(askGraphExists(related.getUri()), "Unrelated vocabulary graph was removed!");
    }

    @Test
    void migrateIdentifierInternalThrowsWhenCalledOutsideOfTransaction() {
        IriMigrationPair iris = iriMigration(Generator.generateUri());
        IriMigrationType type = IriMigrationType.TERM;
        IriMigrationParams params = new IriMigrationParams();

        assertThrows(IllegalTransactionStateException.class, () -> sut.migrateIdentifierInternal(iris, type, params));
    }

    private boolean askGraphExists(URI graph) {
        Objects.requireNonNull(graph);
        return readOnlyTransactional(() -> em.createNativeQuery("ASK {GRAPH ?graph { ?s ?p ?o }}", Boolean.class)
                .setParameter("graph", graph)
                .getSingleResult());
    }
}

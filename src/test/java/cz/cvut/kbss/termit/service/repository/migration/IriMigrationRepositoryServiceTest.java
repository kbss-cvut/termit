package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.listing.FlatTermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.DeleteChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.IdentifierChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.ResourceDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.business.util.TermSelectionParams;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class IriMigrationRepositoryServiceTest extends BaseServiceTestRunner {

    private static final Comparator<AbstractTerm> TERM_LABEL_COMPARATOR =
            Comparator.comparing(t -> t.getLabel(Environment.LANGUAGE));

    @Autowired
    private EntityManager em;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private TermService termService;

    @Autowired
    private DataRepositoryService dataService;

    @Autowired
    private ResourceDao resourceDao;

    @Autowired
    private TermOccurrenceDao termOccurrenceDao;

    @Autowired
    private ChangeRecordDao changeRecordDao;

    @MockitoSpyBean
    private IriMigrationDao iriMigrationDao;

    @Autowired
    private IriMigrationRepositoryService sut;

    private IriMigrationLongRunningTask task;

    @MockitoBean
    private TextAnalysisService textAnalysisService; // no-op analysis service

    private Vocabulary vocabularyA;

    private Vocabulary vocabularyB;

    private List<Term> termsA;

    private List<Term> termsB;

    private User author;

    @Autowired
    private VocabularyRepositoryService vocabularyRepositoryService;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        author.addType(UserRole.ADMIN.getType());
        Environment.setCurrentUser(author);
        transactional(() -> em.persist(author));

        vocabularyA = generateVocabulary();
        vocabularyB = generateVocabulary();
        termsA = generateTerms(vocabularyA, 5);
        termsB = generateTerms(vocabularyB, 5);
        task = new IriMigrationLongRunningTask();
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
        return term;
    }

    private Term generateRootTerm(Vocabulary vocabulary) {
        final Term term = generateTerm(vocabulary);
        termService.persistRoot(term, vocabulary);
        return term;
    }

    private Term generateSubTerm(Vocabulary vocabulary, Term parent) {
        final Term term = generateTerm(vocabulary);
        termService.persistChild(term, parent);
        return term;
    }

    /**
     * Generates {@code count} terms in the vocabulary, the first one in the list is a sub term of the second one, the
     * rest are root terms.
     */
    private List<Term> generateTerms(Vocabulary vocabulary, int count) {
        final List<Term> terms = IntStream.range(0, count - 1)
                .mapToObj(i -> generateRootTerm(vocabulary))
                .collect(Collectors.toCollection(ArrayList::new));
        // the parent must be persisted first, so the sub term is prepended once all root terms exist
        terms.add(generateSubTerm(vocabulary, terms.getFirst()));
        terms.sort(TERM_LABEL_COMPARATOR);
        return terms;
    }

    private CustomAttribute generateCustomAttribute() {
        final CustomAttribute attribute = new CustomAttribute();
        attribute.setUri(Generator.generateUri());
        attribute.setLabel(
                MultilingualString.create("Custom attribute " + Generator.randomInt(), Environment.LANGUAGE));
        dataService.persistCustomAttribute(attribute);
        return attribute;
    }

    /** Generates a file entity stored in the context of the vocabulary */
    private File generateFile(Vocabulary vocabulary) {
        final File file = Generator.generateFileWithId("test.html");
        transactional(() -> resourceDao.persist(file, vocabulary));
        return file;
    }

    /**
     * Generates and persists an occurrence of the term in the target ({@link File} content or {@link Term} definition),
     * the occurrence is stored in the occurrence context of the target.
     *
     * @see TermOccurrence#resolveContext(URI)
     */
    private TermOccurrence generateTermOccurrence(Term term, Asset<?> target) {
        final TermOccurrence occurrence = Generator.generateTermOccurrence(term, target, false);
        transactional(() -> termOccurrenceDao.persist(occurrence));
        return occurrence;
    }

    /**
     * Generates a change record of every type for the term, the records are not persisted.
     *
     * @see AbstractChangeRecord
     */
    private List<AbstractChangeRecord> generateChangeRecordsOfEveryType(Term term) {
        final DeleteChangeRecord deleteRecord = new DeleteChangeRecord(term);
        deleteRecord.setTimestamp(Utils.timestamp());
        deleteRecord.setAuthor(author);

        final IdentifierChangeRecord identifierRecord = new IdentifierChangeRecord();
        identifierRecord.setChangedEntity(term.getUri());
        identifierRecord.setOriginalIdentifier(Generator.generateUri());
        identifierRecord.setTimestamp(Utils.timestamp());
        identifierRecord.setAuthor(author);

        return List.of(
                Generator.generatePersistChange(term),
                Generator.generateUpdateChange(term),
                deleteRecord,
                identifierRecord);
    }

    private static String generateNamespace() {
        return IdentifierResolver.ensureNamespaceSeparatorTermination(Generator.generateUriString());
    }

    /**
     * Changes the preferred namespace of the vocabulary to a random one and persists the change, the identifiers of the
     * vocabulary terms are not changed.
     *
     * @return the new namespace of the vocabulary
     */
    private String changeVocabularyNamespace(Vocabulary vocabulary) {
        final String namespace = generateNamespace();
        transactional(() -> {
            // the namespace must be changed on a managed instance, update restores the namespace of a detached one
            final Vocabulary managed = vocabularyRepositoryService.findRequired(vocabulary.getUri());
            managed.setPreferredNamespaceUri(namespace);
            // persisted by jopa at the end of transaction
        });
        vocabulary.setPreferredNamespaceUri(namespace);

        final Vocabulary persisted = vocabularyRepositoryService.findRequired(vocabulary.getUri());
        assertEquals(namespace, persisted.getPreferredNamespaceUri(), "Vocabulary namespace was not changed!");
        return namespace;
    }

    private IriMigrationPair iriMigration(URI originalIri) {
        return iriMigration(originalIri, vocabularyA.getPreferredNamespaceUri());
    }

    private IriMigrationPair iriMigration(URI originalIri, String namespace) {
        final URI randomUri = Generator.generateUri();
        final String randomFragment = IdentifierResolver.extractIdentifierFragment(randomUri);

        return new IriMigrationPair(originalIri, URI.create(namespace + randomFragment));
    }

    @ParameterizedTest
    @EnumSource(IriMigrationType.class)
    void migrateIdentifierThrowsForNonExistingAsset(IriMigrationType type) {
        final IriMigrationPair iris = iriMigration(Generator.generateUri());
        final IriMigrationParams params = new IriMigrationParams();

        assertThrows(NotFoundException.class, () -> sut.migrateIdentifier(iris, type, params, task));
    }

    @Test
    void migrateIdentifierMigratesTermIdentifier() {
        final Term toMigrate = termsA.getFirst();
        final Term related = termsB.getFirst();

        related.setRelatedMatch(Set.of(toMigrate.toTermInfo()));
        termService.update(related);

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task);

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

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

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

        sut.migrateIdentifier(iris, IriMigrationType.CUSTOM_ATTRIBUTE, params, task);

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

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        assertFalse(askGraphExists(toMigrate.getUri()), "Old vocabulary graph was not migrated!");
        assertTrue(askGraphExists(iris.newIri()), "New vocabulary graph does not exists!");
        assertTrue(askGraphExists(related.getUri()), "Unrelated vocabulary graph was removed!");
    }

    @Test
    void migrateVocabularyIdentifierDoesNotMigrateTermsWhenNamespaceIsNotProvided() {
        final Vocabulary toMigrate = vocabularyA;
        final IriMigrationParams params = new IriMigrationParams();
        migrateVocabularyAndAssertTermsNotChanged(toMigrate, params);
    }

    @Test
    void migrateVocabularyIdentifierDoesNotMigrateTermsWhenNamespaceIsNotChanged() {
        final Vocabulary toMigrate = vocabularyA;
        assertFalse(Utils.isBlank(toMigrate.getPreferredNamespaceUri()));
        final IriMigrationParams params = new IriMigrationParams(URI.create(toMigrate.getPreferredNamespaceUri()));
        migrateVocabularyAndAssertTermsNotChanged(toMigrate, params);
    }

    void migrateVocabularyAndAssertTermsNotChanged(Vocabulary toMigrate, IriMigrationParams params) {
        final IriMigrationPair iris = iriMigration(toMigrate.getUri());

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        verify(iriMigrationDao, times(1)).migrateIdentifier(any());
        verify(iriMigrationDao).migrateIdentifier(iris);

        // ensure every term still exists
        termsA.stream().map(HasIdentifier::getUri).forEach(termService::findRequired);
    }

    @Test
    void migrateVocabularyWithNewNamespaceMigratesAllTerms() {
        final Vocabulary toMigrate = vocabularyA;
        assertFalse(Utils.isBlank(toMigrate.getPreferredNamespaceUri()));

        final String newNamespace = generateNamespace();

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(newNamespace));

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        verify(iriMigrationDao, times(1 + termsA.size())).migrateIdentifier(any());

        final Vocabulary migratedVocabulary = vocabularyService.findRequired(iris.newIri());

        final TermSelectionParams termSelectionParams =
                new TermSelectionParams(true, false, false, false, Pageable.ofSize(termsA.size()));
        final List<? extends AbstractTerm> terms = termService.findAll(migratedVocabulary, termSelectionParams);
        terms.sort(TERM_LABEL_COMPARATOR);

        // ensure no original term exists
        termsA.stream().map(HasIdentifier::getUri).map(termService::find).forEach(term -> {
            assertTrue(term.isEmpty(), "Term not migrated!");
        });

        for (int i = 0; i < terms.size(); i++) {
            final FlatTermDto migratedTerm = (FlatTermDto) terms.get(i);
            final Term originalTerm = termsA.get(i);
            assertTrue(
                    migratedTerm.getUri().toString().startsWith(newNamespace), "Term not migrated to new namespace!");
            assertEquals(originalTerm.getLabel(), migratedTerm.getLabel());
        }
    }

    @Test
    void migrateIdentifierInternalThrowsWhenCalledOutsideOfTransaction() {
        IriMigrationPair iris = iriMigration(Generator.generateUri());
        IriMigrationType type = IriMigrationType.TERM;
        IriMigrationParams params = new IriMigrationParams();

        assertThrows(IllegalTransactionStateException.class, () -> sut.migrateIdentifierInternal(iris, type, params));
    }

    @Test
    void migrateIdentifierMigratesOccurrenceOfTheTermInDocument() {
        final Term toMigrate = termsA.getFirst();
        final File file = generateFile(vocabularyA);
        migrateTermAndAssertOccurrenceMigrated(toMigrate, file);
    }

    @Test
    void migrateIdentifierMigratesOccurrenceOfTheTermInDefinition() {
        final Term toMigrate = termsA.getFirst();
        final Term definedTerm = termsB.getFirst();
        migrateTermAndAssertOccurrenceMigrated(toMigrate, definedTerm);
    }

    /**
     * Generates an occurrence of the {@code toMigrate} term, migrates the term, and asserts the occurrence remained in
     * the same graph and references the migrated term.
     *
     * @param toMigrate the term whose occurrence should be generated and asserted after migration
     * @param occurrenceTarget the target of the occurrence
     */
    void migrateTermAndAssertOccurrenceMigrated(Term toMigrate, Asset<?> occurrenceTarget) {
        final TermOccurrence occurrence = generateTermOccurrence(toMigrate, occurrenceTarget);
        final URI occurrenceGraph = TermOccurrence.resolveContext(occurrenceTarget.getUri());
        assertEquals(occurrence.resolveContext(), occurrenceGraph);

        assertTrue(askGraphExists(occurrenceGraph));

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task);

        // the occurrence must remain in the graph of its target
        final TermOccurrence migratedOcc = findOccurrence(occurrence.getUri(), occurrenceGraph);

        assertEquals(iris.newIri(), migratedOcc.getTerm(), "Occurrence does not reference the migrated term!");
        assertEquals(occurrenceTarget.getUri(), migratedOcc.getTarget().getSource());
    }

    @Test
    void migrateIdentifierMigratesTermOccurrenceGraph() {
        final Term toMigrate = termsA.getFirst();
        final Term occurring = termsB.getFirst();

        // occurrence of another term in the definition of the migrated term
        final TermOccurrence occurrence = generateTermOccurrence(occurring, toMigrate);

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        final URI originalGraph = TermOccurrence.resolveContext(iris.originalIri());
        final URI newGraph = TermOccurrence.resolveContext(iris.newIri());

        assertTrue(askGraphExists(originalGraph));
        assertFalse(askGraphExists(newGraph));

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task);

        assertFalse(askGraphExists(originalGraph), "Old occurrence graph was not migrated!");
        assertTrue(askGraphExists(newGraph), "New occurrence graph does not exist!");

        final TermOccurrence migrated = findOccurrence(occurrence.getUri(), newGraph);

        assertEquals(occurring.getUri(), migrated.getTerm(), "Occurring term was changed!");
        assertEquals(iris.newIri(), migrated.getTarget().getSource(), "Occurrence target is not the migrated term!");
    }

    @Test
    void migrateIdentifierThrowsWhenTermsNewIdentifierDoesNotBelongToUnchangedVocabularyNamespace() {
        final Term toMigrate = termsA.getFirst();
        final String previousNamespace = vocabularyA.getPreferredNamespaceUri();

        changeVocabularyNamespace(vocabularyA);

        // the new identifier is outside the vocabulary namespace, which is not changed by the migration
        final IriMigrationPair iris = iriMigration(toMigrate.getUri(), previousNamespace);
        final IriMigrationParams params = new IriMigrationParams();

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task));
        assertTrue(e.getMessage().contains("does not start with Vocabulary namespace"));
    }

    @Test
    void migrateIdentifierInternalThrowsWhenTermsNewIdentifierDoesNotBelongToNewVocabularyNamespace() {
        final Term toMigrate = termsA.getFirst();

        // the new identifier is inside the current vocabulary namespace, but outside the new one
        final IriMigrationPair iris = iriMigration(toMigrate.getUri(), vocabularyA.getPreferredNamespaceUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(generateNamespace()));

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> transactional(() -> sut.migrateIdentifierInternal(iris, IriMigrationType.TERM, params)));
        assertTrue(e.getMessage().contains("does not start with Vocabulary namespace"));
    }

    // ensures terms within invalid namespace can be migrated to a correct one
    @Test
    void migrateIdentifierDoesNotThrowWhenOriginalTermIdentifierDoesNotBelongToOriginalVocabularyNamespace() {
        final Term toMigrate = termsA.getFirst();

        // the original term identifier remains in the previous namespace
        final String vocabularyNamespace = changeVocabularyNamespace(vocabularyA);

        // the new identifier is inside the vocabulary namespace, which is not changed by the migration
        final IriMigrationPair iris = iriMigration(toMigrate.getUri(), vocabularyNamespace);
        final IriMigrationParams params = new IriMigrationParams();

        assertDoesNotThrow(() -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task));

        assertTrue(termService.find(iris.newIri()).isPresent(), "Term not migrated!");
    }

    // the context is discarded, to disable rdfs inference for other tests
    @DirtiesContext
    @ParameterizedTest
    @EnumSource(
            value = IriMigrationType.class,
            names = {"TERM", "VOCABULARY"})
    void migrateIdentifierCreatesIdentifierChangeRecordForMigratedEntity(IriMigrationType type) {
        final Asset<?> assetToMigrate = type == IriMigrationType.TERM ? termsA.getFirst() : vocabularyA;
        final IriMigrationPair iris = iriMigration(assetToMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, type, params, task);

        // change records are searched by the current identifier of the asset
        assetToMigrate.setUri(iris.newIri());
        // the search requires the hierarchy of change record classes from the ontology
        enableRdfsInference(em);

        final List<IdentifierChangeRecord> records = findIdentifierChangeRecords(assetToMigrate);

        assertEquals(1, records.size());
        final IdentifierChangeRecord record = records.getFirst();
        assertEquals(iris.originalIri(), record.getOriginalIdentifier());
        assertEquals(iris.newIri(), record.getChangedEntity());

        assertEquals(author, record.getAuthor());
    }

    // the context is discarded, to disable rdfs inference for other tests
    @DirtiesContext
    @Test
    void migrateIdentifierCreatesIdentifierChangeRecordForTermsWhenVocabularyNamespaceIsMigrated() {
        final Vocabulary toMigrate = vocabularyA;

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(generateNamespace()));
        final String newNamespace = params.preferredNamespaceUri().toString();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        // the search requires the hierarchy of change record classes from the ontology
        enableRdfsInference(em);

        for (Term term : termsA) {
            final URI originalIri = term.getUri();
            final URI newIri = URI.create(newNamespace + IdentifierResolver.extractIdentifierFragment(originalIri));

            // change records are searched by the current identifier of the term and of its vocabulary
            term.setUri(newIri);
            term.setVocabulary(iris.newIri());

            final List<IdentifierChangeRecord> records = findIdentifierChangeRecords(term);

            assertEquals(1, records.size());
            final IdentifierChangeRecord record = records.getFirst();
            assertEquals(originalIri, record.getOriginalIdentifier());
            assertEquals(newIri, record.getChangedEntity());
        }
    }

    // the context is discarded, to disable rdfs inference for other tests
    @DirtiesContext
    @Test
    void migrateIdentifierCreatesPreferredNamespaceChangeRecord() {
        final Vocabulary toMigrate = vocabularyA;
        final String originalNamespace = toMigrate.getPreferredNamespaceUri();
        assertFalse(Utils.isBlank(originalNamespace));

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(generateNamespace()));
        final String newNamespace = params.preferredNamespaceUri().toString();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        // change records are searched by the current identifier of the asset
        toMigrate.setUri(iris.newIri());
        // the search requires the hierarchy of change record classes from the ontology
        enableRdfsInference(em);

        final List<UpdateChangeRecord> records = changeRecordDao.findAll(toMigrate).stream()
                .filter(UpdateChangeRecord.class::isInstance)
                .map(UpdateChangeRecord.class::cast)
                .filter(record ->
                        record.getChangedAttribute().equals(Vocabulary_.preferredNamespaceUriPropertyIRI.toURI()))
                .toList();

        assertEquals(1, records.size());
        final UpdateChangeRecord record = records.getFirst();
        assertEquals(Set.of(originalNamespace), record.getOriginalValue());
        assertEquals(Set.of(newNamespace), record.getNewValue());
    }

    // the context is discarded, to disable rdfs inference for other tests
    @DirtiesContext
    @Test
    void migrateIdentifierDoesNotModifyIdentifierChangeRecords() {
        final Term toMigrate = termsA.getFirst();
        final URI originalIri = toMigrate.getUri();
        final IriMigrationParams params = new IriMigrationParams();

        final IriMigrationPair firstMigration = iriMigration(originalIri);
        sut.migrateIdentifier(firstMigration, IriMigrationType.TERM, params, task);

        // a new term reuses the identifier released by the first migration
        final Term reusingTerm = generateTerm(vocabularyA);
        reusingTerm.setUri(originalIri);
        termService.persistRoot(reusingTerm, vocabularyA);

        final IriMigrationPair secondMigration = iriMigration(originalIri);
        sut.migrateIdentifier(secondMigration, IriMigrationType.TERM, params, task);

        // change records are searched by the current identifier of the asset
        toMigrate.setUri(firstMigration.newIri());
        // the search requires the hierarchy of change record classes from the ontology
        enableRdfsInference(em);

        final List<IdentifierChangeRecord> records = findIdentifierChangeRecords(toMigrate);

        assertEquals(1, records.size());
        final IdentifierChangeRecord record = records.getFirst();
        assertEquals(
                originalIri,
                record.getOriginalIdentifier(),
                "Original identifier in the identifier change record of the first migration was changed!");
        assertEquals(firstMigration.newIri(), record.getChangedEntity());
    }

    @Test
    void migrateIdentifierThrowsWhenEntityWithTheNewIdentifierAlreadyExists() {
        final IriMigrationPair iris = new IriMigrationPair(
                termsA.getFirst().getUri(), termsB.getFirst().getUri());
        final IriMigrationParams params = new IriMigrationParams();

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void migrateIdentifierDoesNotMigrateTermsAlreadyInNewNamespace() {
        final Term alreadyMigrated = termsA.getFirst();
        final String namespace = generateNamespace();
        final URI alreadyMigratedUri =
                URI.create(namespace + IdentifierResolver.extractIdentifierFragment(alreadyMigrated.getUri()));
        // prepare already migrated term
        transactional(() -> {
            iriMigrationDao.migrateIdentifier(new IriMigrationPair(alreadyMigrated.getUri(), alreadyMigratedUri));
        });
        alreadyMigrated.setUri(alreadyMigratedUri);

        final IriMigrationPair iris = iriMigration(vocabularyA.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(namespace));

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        // no identifier change record was created
        assertEquals(0, findIdentifierChangeRecords(alreadyMigrated).size());
    }

    @Test
    void migrateIdentifierThrowsForVocabularyNamespaceMigrationWhenTermIsNotInVocabularyOriginalNamespace() {
        final Term illegalTerm = termsA.getFirst();
        final String namespace = generateNamespace();
        final URI termUri = URI.create(namespace + IdentifierResolver.extractIdentifierFragment(illegalTerm.getUri()));
        // prepare term that is not in the vocabulary namespace
        transactional(() -> {
            iriMigrationDao.migrateIdentifier(new IriMigrationPair(illegalTerm.getUri(), termUri));
        });
        illegalTerm.setUri(termUri);

        final String newNamespace = generateNamespace();
        final IriMigrationPair iris = iriMigration(vocabularyA.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(newNamespace));

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task));
        assertTrue(e.getMessage().contains("original vocabulary namespace"));
    }

    @Test
    void migrateIdentifierModifiesVersionOfRelationToTerm() {
        final Term toMigrate = termsA.getFirst();
        final Term unchanged = termsA.getLast();

        final Snapshot vocabularySnapshot = vocabularyService.createSnapshot(vocabularyA);
        final URI toMigrateSnapshot = findSnapshotOf(toMigrate.getUri());
        final URI unchangedSnapshot = findSnapshotOf(unchanged.getUri());

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task);

        assertEquals(
                vocabularyA.getUri(),
                findVersionOf(vocabularySnapshot.getUri()),
                "Vocabulary snapshot is not a version of the unchanged vocabulary!");
        assertEquals(
                iris.newIri(),
                findVersionOf(toMigrateSnapshot),
                "Term snapshot is not a version of the migrated term!");
        assertEquals(
                unchanged.getUri(),
                findVersionOf(unchangedSnapshot),
                "Snapshot of another term is not a version of the unchanged term!");
    }

    @Test
    void migrateIdentifierModifiesVersionOfRelationToVocabulary() {
        final Vocabulary toMigrate = vocabularyA;

        final Snapshot snapshot = vocabularyService.createSnapshot(toMigrate);
        assertEquals(toMigrate.getUri(), findVersionOf(snapshot.getUri()));

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        assertEquals(
                iris.newIri(),
                findVersionOf(snapshot.getUri()),
                "Snapshot is not a version of the migrated vocabulary!");
    }

    @Test
    void migrateIdentifierDoesNotModifyCustomAttributeUsageInSnapshot() {
        final CustomAttribute toMigrate = generateCustomAttribute();
        // literal value of the custom attribute
        final Set<Object> value = Set.of("Value of migrated attribute");

        final Term term = termsA.getFirst();
        term.setProperties(new HashMap<>(Map.of(toMigrate.getUri().toString(), value)));
        termService.update(term);

        vocabularyService.createSnapshot(vocabularyA);
        final URI termSnapshot = findSnapshotOf(term.getUri());

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.CUSTOM_ATTRIBUTE, params, task);

        final Map<String, Set<Object>> properties =
                termService.findRequired(termSnapshot).getProperties();

        assertEquals(
                value, properties.get(iris.originalIri().toString()), "Custom attribute in the snapshot was changed!");
        assertFalse(properties.containsKey(iris.newIri().toString()), "Custom attribute in the snapshot was migrated!");
    }

    @Test
    void migrateIdentifierDoesNotModifyCustomAttributeValueInSnapshot() {
        final CustomAttribute attribute = generateCustomAttribute();
        final Term toMigrate = termsA.getFirst();

        final Term term = termsA.getLast();
        // value of the custom attribute is reference to a term that will be migrated (toMigrate)
        term.setProperties(new HashMap<>(Map.of(attribute.getUri().toString(), Set.of(toMigrate.getUri()))));
        termService.update(term);

        vocabularyService.createSnapshot(vocabularyA);
        final URI termSnapshot = findSnapshotOf(term.getUri());

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams();

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task);

        final Map<String, Set<Object>> properties =
                termService.findRequired(termSnapshot).getProperties();

        assertEquals(
                Set.of(iris.originalIri()),
                properties.get(attribute.getUri().toString()),
                "Custom attribute value in the snapshot was migrated!");
    }

    @Test
    void migrateIdentifierThrowsForIdentifierOfVocabularySnapshot() {
        vocabularyService.createSnapshot(vocabularyA);
        final URI vocabularySnapshot = findSnapshotOf(vocabularyA.getUri());
        final IriMigrationPair iris = iriMigration(vocabularySnapshot);
        final IriMigrationParams params = new IriMigrationParams();

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task));
        assertTrue(e.getMessage().contains("snapshot"));
    }

    @Test
    void migrateIdentifierThrowsForIdentifierOfTermSnapshot() {
        vocabularyService.createSnapshot(vocabularyA);
        final URI termSnapshot = findSnapshotOf(termsA.getFirst().getUri());
        final IriMigrationPair iris = iriMigration(termSnapshot);
        final IriMigrationParams params = new IriMigrationParams();

        final TermItException e = assertThrows(
                InvalidParameterException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params, task));
        assertTrue(e.getMessage().contains("snapshot"));
    }

    @Test
    void migrationIsReversible() {
        final Vocabulary toMigrate = vocabularyA;
        final URI originalNamespace = URI.create(toMigrate.getPreferredNamespaceUri());
        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(generateNamespace()));

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        final IriMigrationPair reverseIris = new IriMigrationPair(iris.newIri(), iris.originalIri());
        final IriMigrationParams reverseParams = new IriMigrationParams(originalNamespace);

        sut.migrateIdentifier(reverseIris, IriMigrationType.VOCABULARY, reverseParams, task);

        assertTrue(askGraphExists(iris.originalIri()));
        assertFalse(askGraphExists(iris.newIri()));
    }

    // the context is discarded, to disable rdfs inference for other tests
    @DirtiesContext
    @Test
    void migrateIdentifierMigratesChangedEntityOfChangeRecords() {
        final Vocabulary toMigrate = vocabularyA;
        final Term term = termsA.getFirst();
        final URI originalTermIri = term.getUri();

        final List<AbstractChangeRecord> records = generateChangeRecordsOfEveryType(term);
        transactional(() -> records.forEach(record -> changeRecordDao.persist(record, term)));

        final IriMigrationPair iris = iriMigration(toMigrate.getUri());
        final IriMigrationParams params = new IriMigrationParams(URI.create(generateNamespace()));
        final String newNamespace = params.preferredNamespaceUri().toString();

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params, task);

        final URI newTermIri = URI.create(newNamespace + IdentifierResolver.extractIdentifierFragment(originalTermIri));

        // change records are searched by the current identifier of the term and of its vocabulary
        term.setUri(newTermIri);
        term.setVocabulary(iris.newIri());
        // the search requires the hierarchy of change record classes from the ontology
        enableRdfsInference(em);

        final Map<URI, AbstractChangeRecord> migratedRecords = changeRecordDao.findAll(term).stream()
                .collect(Collectors.toMap(AbstractChangeRecord::getUri, Function.identity()));

        for (AbstractChangeRecord record : records) {
            final String recordType = record.getClass().getSimpleName();
            final AbstractChangeRecord migrated = migratedRecords.get(record.getUri());

            assertNotNull(migrated, recordType + " was not kept by the migrated term!");
            assertEquals(record.getClass(), migrated.getClass());
            assertEquals(
                    newTermIri, migrated.getChangedEntity(), recordType + " does not reference the migrated term!");
        }
    }

    private List<IdentifierChangeRecord> findIdentifierChangeRecords(Asset<?> asset) {
        return changeRecordDao.findAll(asset).stream()
                .filter(IdentifierChangeRecord.class::isInstance)
                .map(IdentifierChangeRecord.class::cast)
                .toList();
    }

    /** Finds the snapshot of the asset, fails when the asset does not have exactly one snapshot. */
    private URI findSnapshotOf(URI asset) {
        Objects.requireNonNull(asset);
        final List<URI> snapshots = readOnlyTransactional(() -> em.createNativeQuery("""
                            SELECT DISTINCT ?snapshot WHERE {
                                ?snapshot ?isVersionOf ?asset .
                                FILTER (?isVersionOf IN (?relations))
                            }
                        """, URI.class)
                .setParameter("asset", asset)
                .setParameter("relations", Constants.IS_VERSION_OF_RELATIONS)
                .getResultList());
        assertEquals(1, snapshots.size(), "Expected exactly one snapshot of " + Utils.uriToString(asset));
        return snapshots.getFirst();
    }

    /** Finds the asset the snapshot is a version of, fails when the snapshot is not a version of exactly one asset. */
    private URI findVersionOf(URI snapshot) {
        Objects.requireNonNull(snapshot);
        final List<URI> assets = readOnlyTransactional(() -> em.createNativeQuery("""
                           SELECT DISTINCT ?asset WHERE {
                               ?snapshot ?isVersionOf ?asset .
                               FILTER (?isVersionOf IN (?relations))
                           }
                       """, URI.class)
                .setParameter("snapshot", snapshot)
                .setParameter("relations", Constants.IS_VERSION_OF_RELATIONS)
                .getResultList());
        assertEquals(
                1, assets.size(), "Expected " + Utils.uriToString(snapshot) + " to be a version of exactly one asset");
        return assets.getFirst();
    }

    /** Finds the occurrence in the specified graph, fails when the graph does not contain the occurrence. */
    private TermOccurrence findOccurrence(URI occurrence, URI graph) {
        Objects.requireNonNull(occurrence);
        Objects.requireNonNull(graph);
        final TermOccurrence result =
                readOnlyTransactional(() -> em.find(TermOccurrence.class, occurrence, new EntityDescriptor(graph)));
        assertNotNull(result, "Occurrence not found in graph " + Utils.uriToString(graph));
        return result;
    }

    private boolean askGraphExists(URI graph) {
        Objects.requireNonNull(graph);
        return readOnlyTransactional(() -> em.createNativeQuery("ASK {GRAPH ?graph { ?s ?p ?o }}", Boolean.class)
                .setParameter("graph", graph)
                .getSingleResult());
    }
}

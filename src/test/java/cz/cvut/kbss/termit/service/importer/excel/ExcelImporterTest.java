package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.VocabularyDoesNotExistException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImporterTest {

    @Mock
    private VocabularyDao vocabularyDao;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private Consumer<Vocabulary> prePersist;

    @Mock
    private DataDao dataDao;

    @Spy
    private Configuration config = new Configuration();

    @SuppressWarnings("unused")
    @Mock
    private EntityManager em;

    @SuppressWarnings("unused")
    @Spy
    private IdentifierResolver idResolver = new IdentifierResolver();

    @InjectMocks
    private ExcelImporter sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        config.getNamespace().getTerm().setSeparator("/terms");
    }

    @ParameterizedTest
    @CsvSource({Constants.MediaType.EXCEL, "application/vnd.ms-excel"})
    void supportsMediaTypeReturnsTrueForSupportedExcelMediaType(String mediaType) {
        assertTrue(ExcelImporter.supportsMediaType(mediaType));
    }

    @Test
    void supportsMediaTypeReturnsFalseForUnsupportedMediaType() {
        assertFalse(ExcelImporter.supportsMediaType("application/json"));
    }

    @Test
    void importThrowsVocabularyDoesNotExistExceptionWhenNoVocabularyIdentifierIsProvided() {
        assertThrows(VocabularyDoesNotExistException.class,
                     () -> sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, null, prePersist),
                                                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                   Environment.loadFile(
                                                                                           "data/import-simple-en.xlsx"))));
    }

    @Test
    void importThrowsVocabularyDoesNotExistExceptionWhenVocabularyIsNotFound() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(false);
        assertThrows(VocabularyDoesNotExistException.class,
                     () -> sut.importVocabulary(
                             new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                             new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                Environment.loadFile(
                                                                        "data/import-simple-en.xlsx"))));
    }

    @Test
    void importCreatesRootTermsWithBasicAttributesFromEnglishSheet() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-simple-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Definition of term Building", building.get().getDefinition().get("en"));
        assertEquals("Building scope note", building.get().getDescription().get("en"));
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("The process of building a building", construction.get().getDefinition().get("en"));
    }

    private void initIdentifierGenerator(String lang, boolean forChild) {
        doAnswer(inv -> {
            final Term t = inv.getArgument(0);
            if (t.getUri() != null) {
                return null;
            }
            t.setUri(URI.create(vocabulary.getUri().toString() + "/" + IdentifierResolver.normalize(
                    t.getLabel().get(lang))));
            return null;
        }).when(termService).addRootTermToVocabulary(any(Term.class), eq(vocabulary));
        if (forChild) {
            doAnswer(inv -> {
                final Term t = inv.getArgument(0);
                if (t.getUri() != null) {
                    return null;
                }
                t.setUri(URI.create(vocabulary.getUri().toString() + "/" + IdentifierResolver.normalize(
                        t.getLabel().get(lang))));
                return null;
            }).when(termService).addChildTerm(any(Term.class), any(Term.class));
        }
    }

    @Test
    void importCreatesRootTermsWithPluralBasicAttributesFromEnglishSheet() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-plural-atts-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(1, captor.getAllValues().size());
        final Term building = captor.getValue();
        assertEquals("Definition of term Building", building.getDefinition().get("en"));
        assertEquals("Building scope note", building.getDescription().get("en"));
        assertEquals(Set.of(
                MultilingualString.create("Construction", "en"),
                MultilingualString.create("Structure", "en"),
                MultilingualString.create("House", "en")
        ), building.getAltLabels());
        assertEquals(Set.of(
                MultilingualString.create("bldng", "en"),
                MultilingualString.create("strctr", "en"),
                MultilingualString.create("haus", "en")
        ), building.getHiddenLabels());
        assertEquals(Set.of(
                MultilingualString.create("Dancing house", "en")
        ), building.getExamples());
        assertEquals(Set.of("B"), building.getNotations());
        assertEquals(Set.of("a56"), building.getProperties().get(DC.Terms.REFERENCES));
    }

    @Test
    void importCreatesRootTermsWithBasicAttributesFromMultipleTranslationSheets() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-simple-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Budova", building.get().getLabel().get("cs"));
        assertEquals("Definition of term Building", building.get().getDefinition().get("en"));
        assertEquals("Definice pojmu budova", building.get().getDefinition().get("cs"));
        assertEquals("Building scope note", building.get().getDescription().get("en"));
        assertEquals("Doplňující poznámka pojmu budova", building.get().getDescription().get("cs"));
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("Stavba", construction.get().getLabel().get("cs"));
        assertEquals("The process of building a building", construction.get().getDefinition().get("en"));
        assertEquals("Proces výstavby budovy", construction.get().getDefinition().get("cs"));
    }

    @Test
    void importCreatesRootTermsWithPluralBasicAttributesFromMultipleTranslationSheets() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-plural-atts-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(1, captor.getAllValues().size());
        final Term building = captor.getValue();
        assertEquals("Budova", building.getLabel().get("cs"));
        assertTrue(building.getAltLabels().stream()
                           .anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("Structure")));
        assertTrue(building.getAltLabels().stream()
                           .anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("House")));
        assertTrue(
                building.getAltLabels().stream().anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("dům")));
        assertTrue(building.getAltLabels().stream()
                           .anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("stavba")));
        assertTrue(building.getHiddenLabels().stream()
                           .anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("bldng")));
        assertTrue(building.getHiddenLabels().stream()
                           .anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("barák")));
        assertTrue(building.getExamples().stream()
                           .anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("Dancing house")));
        assertTrue(building.getExamples().stream()
                           .anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("Tančící dům")));
        assertEquals(Set.of("B"), building.getNotations());
        assertEquals(Set.of("a56"), building.getProperties().get(DC.Terms.REFERENCES));
    }

    @Test
    void importCreatesTermHierarchy() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, true);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-hierarchy-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> rootCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(rootCaptor.capture(), eq(vocabulary));
        assertEquals(1, rootCaptor.getAllValues().size());
        final Term area = rootCaptor.getValue();
        assertEquals("Area", area.getLabel().get("en"));
        final ArgumentCaptor<Term> childCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addChildTerm(childCaptor.capture(), eq(area));
        assertEquals(1, childCaptor.getAllValues().size());
        final Term buildableArea = childCaptor.getValue();
        assertEquals("Buildable area", buildableArea.getLabel().get("en"));
    }

    @Test
    void importSavesRelationshipsBetweenTerms() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-references-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(termCaptor.capture(), eq(vocabulary));
        final ArgumentCaptor<Collection<Quad>> quadsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataDao).insertRawData(quadsCaptor.capture());
        assertEquals(1, quadsCaptor.getValue().size());
        assertEquals(List.of(new Quad(termCaptor.getAllValues().get(1).getUri(), URI.create(SKOS.RELATED),
                                      termCaptor.getAllValues().get(0).getUri(), vocabulary.getUri())),
                     quadsCaptor.getValue());
    }

    @Test
    void importImportsTermsWhenAnotherLanguageSheetIsEmpty() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-en-empty-cs.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Definition of term Building", building.get().getDefinition().get("en"));
        assertEquals("Building scope note", building.get().getDescription().get("en"));
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("The process of building a building", construction.get().getDefinition().get("en"));
    }

    @Test
    void importFallsBackToEnglishColumnLabelsForUnknownLanguages() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator("de", false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-simple-de.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Gebäude".equals(t.getLabel().get("de"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Definition für ein Gebäude", building.get().getDefinition().get("de"));
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Bau".equals(t.getLabel().get("de"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("Ein Prozess", construction.get().getDefinition().get("de"));
    }

    @Test
    void importSupportsTermIdentifiers() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-identifiers-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals(URI.create("http://example.com/terms/building"), building.get().getUri());
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals(URI.create("http://example.com/terms/construction"), construction.get().getUri());
        final ArgumentCaptor<Collection<Quad>> quadsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataDao).insertRawData(quadsCaptor.capture());
        assertEquals(1, quadsCaptor.getValue().size());
        assertEquals(List.of(new Quad(construction.get().getUri(), URI.create(SKOS.RELATED),
                                      building.get().getUri(), vocabulary.getUri())), quadsCaptor.getValue());
    }

    @Test
    void importSupportsPrefixedTermIdentifiers() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-prefixed-identifiers-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream()
                                              .filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals(URI.create("http://example.com/terms/building"), building.get().getUri());
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals(URI.create("http://example.com/terms/construction"), construction.get().getUri());
        final ArgumentCaptor<Collection<Quad>> quadsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataDao).insertRawData(quadsCaptor.capture());
        assertEquals(1, quadsCaptor.getValue().size());
        assertEquals(List.of(new Quad(construction.get().getUri(), URI.create(SKOS.RELATED),
                                      building.get().getUri(), vocabulary.getUri())), quadsCaptor.getValue());
    }

    @Test
    void importAdjustsTermIdentifiersToUseExistingVocabularyIdentifierAndSeparatorAsNamespace() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-identifiers-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertTrue(captor.getAllValues().stream().anyMatch(t -> Objects.equals(URI.create(
                                                                                       vocabulary.getUri().toString() + config.getNamespace().getTerm().getSeparator() + "/building"),
                                                                               t.getUri())));
        assertTrue(captor.getAllValues().stream().anyMatch(t -> Objects.equals(URI.create(
                                                                                       vocabulary.getUri().toString() + config.getNamespace().getTerm().getSeparator() + "/construction"),
                                                                               t.getUri())));
    }

    @Test
    void importRemovesExistingInstanceWhenImportedTermAlreadyExists() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);
        final Term existingBuilding = Generator.generateTermWithId();
        existingBuilding.setUri(URI.create("http://example.com/terms/building"));
        final Term existingConstruction = Generator.generateTermWithId();
        existingConstruction.setUri(URI.create("http://example.com/terms/construction"));
        when(termService.exists(existingBuilding.getUri())).thenReturn(true);
        when(termService.exists(existingConstruction.getUri())).thenReturn(true);
        when(termService.findRequired(existingBuilding.getUri())).thenReturn(existingBuilding);
        when(termService.findRequired(existingConstruction.getUri())).thenReturn(existingConstruction);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-identifiers-en.xlsx")));
        assertEquals(vocabulary, result);
        verify(termService).forceRemove(existingBuilding);
        verify(termService).forceRemove(existingConstruction);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
    }

    @Test
    void importSupportsReferencesToOtherVocabulariesViaTermIdentifiersWhenReferencedTermsExist() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        initIdentifierGenerator(Constants.DEFAULT_LANGUAGE, false);
        when(termService.exists(any())).thenReturn(false);
        when(termService.exists(URI.create("http://example.com/another-vocabulary/terms/relatedMatch"))).thenReturn(
                true);
        when(termService.exists(URI.create("http://example.com/another-vocabulary/terms/exactMatch"))).thenReturn(true);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-external-references-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(termCaptor.capture(), eq(vocabulary));
        final ArgumentCaptor<Collection<Quad>> quadsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataDao).insertRawData(quadsCaptor.capture());
        assertEquals(2, quadsCaptor.getValue().size());
        assertEquals(List.of(new Quad(termCaptor.getAllValues().get(0).getUri(), URI.create(SKOS.RELATED_MATCH),
                                      URI.create("http://example.com/another-vocabulary/terms/relatedMatch"),
                                      vocabulary.getUri()),
                             (new Quad(termCaptor.getAllValues().get(1).getUri(), URI.create(SKOS.EXACT_MATCH),
                                       URI.create("http://example.com/another-vocabulary/terms/exactMatch"),
                                       vocabulary.getUri()))),
                     quadsCaptor.getValue());
    }
}

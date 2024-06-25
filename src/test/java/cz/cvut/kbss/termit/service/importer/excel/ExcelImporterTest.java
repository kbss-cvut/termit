package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.importing.VocabularyDoesNotExistException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private ExcelImporter sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
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
                     () -> sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                                                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                   Environment.loadFile(
                                                                                           "data/import-simple-en.xlsx"))));
    }

    @Test
    void importCreatesRootTermsWithBasicAttributesFromEnglishSheet() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));

        final Vocabulary result = sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                                                       new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                          Environment.loadFile(
                                                                                                  "data/import-simple-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream().filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Definition of term Building", building.get().getDefinition().get("en"));
        assertEquals("Building scope note", building.get().getDescription().get("en"));
        final Optional<Term> construction = captor.getAllValues().stream().filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("The process of building a building", construction.get().getDefinition().get("en"));
    }

    @Test
    void importCreatesRootTermsWithPluralBasicAttributesFromEnglishSheet() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));

        final Vocabulary result = sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
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

        final Vocabulary result = sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                                                       new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                          Environment.loadFile(
                                                                                                  "data/import-simple-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(2, captor.getAllValues().size());
        final Optional<Term> building = captor.getAllValues().stream().filter(t -> "Building".equals(t.getLabel().get("en"))).findAny();
        assertTrue(building.isPresent());
        assertEquals("Budova", building.get().getLabel().get("cs"));
        assertEquals("Definition of term Building", building.get().getDefinition().get("en"));
        assertEquals("Definice pojmu budova", building.get().getDefinition().get("cs"));
        assertEquals("Building scope note", building.get().getDescription().get("en"));
        assertEquals("Doplňující poznámka pojmu budova", building.get().getDescription().get("cs"));
        final Optional<Term> construction = captor.getAllValues().stream().filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals("Stavba", construction.get().getLabel().get("cs"));
        assertEquals("The process of building a building", construction.get().getDefinition().get("en"));
        assertEquals("Proces výstavby budovy", construction.get().getDefinition().get("cs"));
    }

    @Test
    void importCreatesRootTermsWithPluralBasicAttributesFromMultipleTranslationSheets() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));

        final Vocabulary result = sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                                                       new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                                                          Environment.loadFile(
                                                                                                  "data/import-with-plural-atts-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertEquals(1, captor.getAllValues().size());
        final Term building = captor.getValue();
        assertEquals("Budova", building.getLabel().get("cs"));
        assertTrue(building.getAltLabels().stream().anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("Structure")));
        assertTrue(building.getAltLabels().stream().anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("House")));
        assertTrue(building.getAltLabels().stream().anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("dům")));
        assertTrue(building.getAltLabels().stream().anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("stavba")));
        assertTrue(building.getHiddenLabels().stream().anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("bldng")));
        assertTrue(building.getHiddenLabels().stream().anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("barák")));
        assertTrue(building.getExamples().stream().anyMatch(mls -> mls.get("en") != null && mls.get("en").equals("Dancing house")));
        assertTrue(building.getExamples().stream().anyMatch(mls -> mls.get("cs") != null && mls.get("cs").equals("Tančící dům")));
        assertEquals(Set.of("B"), building.getNotations());
        assertEquals(Set.of("a56"), building.getProperties().get(DC.Terms.REFERENCES));
    }
}

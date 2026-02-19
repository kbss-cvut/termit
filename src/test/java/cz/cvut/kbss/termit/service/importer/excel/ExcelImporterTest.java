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
package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.TermReferencesUpdatedEvent;
import cz.cvut.kbss.termit.exception.importing.ReferencedTermInUnrelatedVocabularyException;
import cz.cvut.kbss.termit.exception.importing.VocabularyDoesNotExistException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.importer.VocabularyImporter;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImporterTest {

    private static final String SEPARATOR = "/terms";

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

    @Mock
    private LanguageService languageService;

    @SuppressWarnings("unused")
    @Mock
    private EntityManager em;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unused")
    @Spy
    private IdentifierResolver idResolver = new IdentifierResolver(config);

    @Mock
    private VocabularyNamespaceResolver namespaceResolver;

    @InjectMocks
    private ExcelImporter sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setUri(URI.create("http://example.com"));
        config.getPersistence().setLanguage(Environment.LANGUAGE);
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
        doAnswer(inv -> inv.getArgument(0).toString() + SEPARATOR).when(namespaceResolver)
                                                                  .resolveNamespace(any(URI.class));

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-simple-en.xlsx")));
        assertEquals(vocabulary, result);
        verifyBasicEnglishTermsAdded();
    }

    @Test
    void importCreatesRootTermsWithPluralBasicAttributesFromEnglishSheet() {
        initVocabularyResolution();

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
        initVocabularyResolution();

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
        initVocabularyResolution();

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
        initVocabularyResolution();

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
        initVocabularyResolution();

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
        // Language sheet has rows but they are empty
        initVocabularyResolution();

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-en-empty-cs.xlsx")));
        assertEquals(vocabulary, result);
        verifyBasicEnglishTermsAdded();
    }

    private void verifyBasicEnglishTermsAdded() {
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
    void importUsesTermsFromOtherSheetWhenCurrentTranslationSheetHasNoRows() {
        // Language sheet has no rows
        initVocabularyResolution();

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile("data/import-en-empty-cs-no-rows.xlsx")));
        assertEquals(vocabulary, result);
        verifyBasicEnglishTermsAdded();
    }

    @Test
    void importFallsBackToEnglishColumnLabelsForUnknownLanguages() {
        initVocabularyResolution();

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
        initVocabularyResolution();

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

    private void initVocabularyResolution() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        doAnswer(inv -> inv.getArgument(0).toString() + SEPARATOR).when(namespaceResolver)
                                                                  .resolveNamespace(any(URI.class));
    }

    @Test
    void importSupportsPrefixedTermIdentifiers() {
        initVocabularyResolution();

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
        initVocabularyResolution();

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-identifiers-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService, times(2)).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertTrue(captor.getAllValues().stream().anyMatch(t -> Objects.equals(URI.create(
                                                                                       vocabulary.getUri().toString() + SEPARATOR + "/building"),
                                                                               t.getUri())));
        assertTrue(captor.getAllValues().stream().anyMatch(t -> Objects.equals(URI.create(
                                                                                       vocabulary.getUri().toString() + SEPARATOR + "/construction"),
                                                                               t.getUri())));
    }

    @Test
    void importRemovesExistingInstanceWhenImportedTermAlreadyExists() {
        initVocabularyResolution();
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
        initVocabularyResolution();
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
        assertThat(quadsCaptor.getValue(),
                   hasItems(new Quad(termCaptor.getAllValues().get(0).getUri(), URI.create(SKOS.RELATED_MATCH),
                                     URI.create("http://example.com/another-vocabulary/terms/relatedMatch"),
                                     vocabulary.getUri()),
                            new Quad(termCaptor.getAllValues().get(1).getUri(), URI.create(SKOS.EXACT_MATCH),
                                      URI.create("http://example.com/another-vocabulary/terms/exactMatch"),
                                      vocabulary.getUri())));
    }

    @Test
    void importResolvesTermStateAndTypesUsingLabels() {
        initVocabularyResolution();
        final Term type = Generator.generateTermWithId();
        type.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/ufo/object-type"));
        type.setLabel(MultilingualString.create("Object Type", Constants.DEFAULT_LANGUAGE));
        type.getLabel().set("cs", "Typ objektu");
        when(languageService.getTermTypes()).thenReturn(List.of(type));
        final RdfsResource state = new RdfsResource(
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/navrhovaný-pojem"),
                MultilingualString.create("Proposed term", Constants.DEFAULT_LANGUAGE), null,
                "http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/stav-pojmu");
        when(languageService.getTermStates()).thenReturn(List.of(state));


        sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-type-state-en.xlsx")));
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        final Term result = captor.getValue();
        assertEquals(Set.of(type.getUri().toString()), result.getTypes());
        assertEquals(state.getUri(), result.getState());
    }

    @Test
    void importSetsConfiguredInitialTermStateWhenSheetDoesNotSpecifyIt() {
        initVocabularyResolution();
        final RdfsResource state = new RdfsResource(
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/navrhovaný-pojem"),
                MultilingualString.create("Proposed term", Constants.DEFAULT_LANGUAGE), null,
                "http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/stav-pojmu");
        when(languageService.getInitialTermState()).thenReturn(Optional.of(state));

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
        assertEquals(state.getUri(), building.get().getState());
        final Optional<Term> construction = captor.getAllValues().stream()
                                                  .filter(t -> "Construction".equals(t.getLabel().get("en"))).findAny();
        assertTrue(construction.isPresent());
        assertEquals(state.getUri(), construction.get().getState());
    }

    @Test
    void importThrowsVocabularyImportExceptionWhenSheetContainsDuplicateLabels() throws Exception {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Workbook input = new XSSFWorkbook(Environment.loadFile("template/termit-import.xlsx"));
        final Sheet sheet = input.getSheet("English");
        sheet.getRow(1).getCell(0).setCellValue("Construction");
        sheet.getRow(2).getCell(0).setCellValue("Construction");
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        input.write(bos);

        final VocabularyImportException ex = assertThrows(VocabularyImportException.class,
                                                          () -> sut.importVocabulary(
                                                                  new VocabularyImporter.ImportConfiguration(false,
                                                                                                             vocabulary.getUri(),
                                                                                                             prePersist),
                                                                  new VocabularyImporter.ImportInput(
                                                                          Constants.MediaType.EXCEL,
                                                                          new ByteArrayInputStream(
                                                                                  bos.toByteArray()))));
        assertEquals("error.vocabulary.import.excel.duplicateLabel", ex.getMessageId());
        verify(termService, never()).addRootTermToVocabulary(any(), eq(vocabulary));
    }

    @Test
    void importThrowsVocabularyImportExceptionWhenSheetContainsDuplicateIdentifiers() throws Exception {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Workbook input = new XSSFWorkbook(Environment.loadFile("template/termit-import.xlsx"));
        final Sheet sheet = input.getSheet("English");
        sheet.shiftColumns(0, 12, 1);
        sheet.getRow(0).createCell(0).setCellValue("Identifier");
        sheet.getRow(1).createCell(0).setCellValue("http://example.com/terms/Construction");
        sheet.getRow(1).getCell(1).setCellValue("Construction");
        sheet.getRow(2).createCell(0).setCellValue("http://example.com/terms/Construction");
        sheet.getRow(2).getCell(1).setCellValue("Another Construction");
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        input.write(bos);

        final VocabularyImportException ex = assertThrows(VocabularyImportException.class,
                                                          () -> sut.importVocabulary(
                                                                  new VocabularyImporter.ImportConfiguration(false,
                                                                                                             vocabulary.getUri(),
                                                                                                             prePersist),
                                                                  new VocabularyImporter.ImportInput(
                                                                          Constants.MediaType.EXCEL,
                                                                          new ByteArrayInputStream(
                                                                                  bos.toByteArray()))));
        assertEquals("error.vocabulary.import.excel.duplicateIdentifier", ex.getMessageId());
        verify(termService, never()).addRootTermToVocabulary(any(), eq(vocabulary));
    }

    @Test
    void importSupportsSpecifyingStateAndTypeOnlyInOneSheet() throws Exception {
        initVocabularyResolution();
        final Workbook input = new XSSFWorkbook(Environment.loadFile("template/termit-import.xlsx"));
        final Sheet englishSheet = input.getSheet("English");
        englishSheet.getRow(1).createCell(0).setCellValue("Construction");
        final Sheet czechSheet = input.getSheet("Czech");
        czechSheet.getRow(1).createCell(0).setCellValue("Konstrukce");
        czechSheet.getRow(1).createCell(10).setCellValue("Publikovaný pojem");
        czechSheet.getRow(1).createCell(5).setCellValue("Typ objektu");
        final Term type = Generator.generateTermWithId();
        type.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/ufo/object-type"));
        type.setLabel(MultilingualString.create("Object Type", Constants.DEFAULT_LANGUAGE));
        type.getLabel().set("cs", "Typ objektu");
        when(languageService.getTermTypes()).thenReturn(List.of(type));
        final RdfsResource state = new RdfsResource(
                URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/pojem/publikovaný-pojem"),
                MultilingualString.create("Published term", Constants.DEFAULT_LANGUAGE), null,
                "http://onto.fel.cvut.cz/ontologies/slovník/agendový/popis-dat/pojem/stav-pojmu");
        state.getLabel().set("cs", "Publikovaný pojem");
        when(languageService.getTermStates()).thenReturn(List.of(state));
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        input.write(bos);

        sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   new ByteArrayInputStream(bos.toByteArray())));
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertThat(captor.getValue().getTypes(), hasItem(type.getUri().toString()));
        assertEquals(state.getUri(), captor.getValue().getState());
        verify(languageService, never()).getInitialTermState();
    }

    @Test
    void importThrowsVocabularyImportExceptionWhenVocabularyAlreadyContainsTermWithSameLabelAndDifferentIdentifier() {
        initVocabularyResolution();
        when(termService.findIdentifierByLabel(any(), any(), any())).thenReturn(Optional.empty());
        doReturn(Optional.of(URI.create(vocabulary.getUri() + SEPARATOR + "/Construction"))).when(
                termService).findIdentifierByLabel("Construction", vocabulary, Constants.DEFAULT_LANGUAGE);


        assertThrows(VocabularyImportException.class, () -> sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-simple-en.xlsx"))));
    }

    @Test
    void importSupportsMultipleTypesDeclaredForTerm() throws Exception {
        initVocabularyResolution();
        final Workbook input = new XSSFWorkbook(Environment.loadFile("template/termit-import.xlsx"));
        final Sheet englishSheet = input.getSheet("English");
        englishSheet.getRow(1).createCell(0).setCellValue("Construction");
        final Term objectType = Generator.generateTermWithId();
        objectType.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/ufo/object-type"));
        objectType.setLabel(MultilingualString.create("Object Type", Constants.DEFAULT_LANGUAGE));
        final Term eventType = Generator.generateTermWithId();
        eventType.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/ufo/event-type"));
        eventType.setLabel(MultilingualString.create("Event Type", Constants.DEFAULT_LANGUAGE));
        when(languageService.getTermTypes()).thenReturn(List.of(objectType, eventType));
        englishSheet.getRow(1).createCell(5)
                    .setCellValue(objectType.getLabel().get() + ";" + eventType.getLabel().get());
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        input.write(bos);

        sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   new ByteArrayInputStream(bos.toByteArray())));
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertThat(captor.getValue().getTypes(),
                   hasItems(objectType.getUri().toString(), eventType.getUri().toString()));
    }

    @Test
    void importTermTranslationsFromExcelWithIdentifiersUpdatesExistingTerms() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Term building = initTermBuilding();
        final Term construction = initTermConstruction();

        final Vocabulary result = sut.importTermTranslations(vocabulary.getUri(), new VocabularyImporter.ImportInput(
                Constants.MediaType.EXCEL,
                Environment.loadFile("data/import-with-identifiers-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        assertEquals("Budova", building.getLabel().get("cs"));
        List.of("Barák", "Dům").forEach(t -> assertTrue(
                building.getAltLabels().stream().anyMatch(mls -> mls.contains("cs") && mls.get("cs").equals(t))));
        assertEquals("Definice pojmu budova", building.getDefinition().get("cs"));
        assertEquals("Doplňující poznámka pojmu budova", building.getDescription().get("cs"));
        assertEquals("Stavba", construction.getLabel().get("cs"));
        assertEquals("Proces výstavby budovy", construction.getDefinition().get("cs"));
        assertTrue(construction.getAltLabels().stream()
                               .anyMatch(mls -> mls.contains("cs") && mls.get("cs").equals("Staveniště")));
        verify(termService).update(building);
        verify(termService).update(construction);
    }

    private Term initTermBuilding() {
        final Term building = new Term(URI.create("http://example.com/terms/budova"));
        building.setLabel(MultilingualString.create("Building", "en"));
        building.setAltLabels(new HashSet<>(Set.of(MultilingualString.create("Complex", "en"))));
        building.setDefinition(MultilingualString.create("Definition of term Building", "en"));
        building.setDescription(MultilingualString.create("Building scope note", "en"));
        building.setHiddenLabels(new HashSet<>());
        building.setExamples(new HashSet<>());
        building.setVocabulary(vocabulary.getUri());
        when(termService.find(building.getUri())).thenReturn(Optional.of(building));
        return building;
    }

    private Term initTermConstruction() {
        final Term construction = new Term(URI.create("http://example.com/terms/stavba"));
        construction.setLabel(MultilingualString.create("Construction", "en"));
        construction.setAltLabels(new HashSet<>(Set.of(MultilingualString.create("Construction site", "en"))));
        construction.setDefinition(MultilingualString.create("The process of building a building", "en"));
        construction.setHiddenLabels(new HashSet<>());
        construction.setExamples(new HashSet<>());
        construction.setVocabulary(vocabulary.getUri());
        when(termService.find(construction.getUri())).thenReturn(Optional.of(construction));
        return construction;
    }

    @Test
    void importTermTranslationsPreservesExistingValues() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Term building = initTermBuilding();

        final Vocabulary result = sut.importTermTranslations(vocabulary.getUri(), new VocabularyImporter.ImportInput(
                Constants.MediaType.EXCEL,
                Environment.loadFile("data/import-with-identifiers-en-cs.xlsx")));
        assertEquals(vocabulary, result);
        assertEquals("Building", building.getLabel().get("en"));
        assertEquals("Definition of term Building", building.getDefinition().get("en"));
        assertTrue(building.getAltLabels().stream()
                           .anyMatch(mls -> mls.contains("en") && mls.get("en").equals("Complex")));
    }

    @Test
    void importTermTranslationsUsesTermLabelToResolveIdentifierWhenExcelDoesNotContainIdentifiers() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        doAnswer(inv -> inv.getArgument(0).toString() + SEPARATOR).when(namespaceResolver)
                                                                  .resolveNamespace(any(URI.class));
        vocabulary.setPrimaryLanguage("cs");
        final Term building = initTermBuilding();

        sut.importTermTranslations(vocabulary.getUri(), new VocabularyImporter.ImportInput(
                Constants.MediaType.EXCEL,
                Environment.loadFile("data/import-simple-en-cs.xlsx")));
        verify(termService).find(building.getUri());
        assertEquals("Budova", building.getLabel().get("cs"));
        verify(termService).update(any(Term.class));
    }

    @Test
    void importTermTranslationsThrowsVocabularyImportExceptionWhenExcelDoesNotContainIdentifierAndSheetWithLabelsInPrimaryLanguage() {
        vocabulary.setUri(URI.create("http://example.com"));
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));

        VocabularyImportException ex = assertThrows(VocabularyImportException.class,
                                                    () -> sut.importTermTranslations(vocabulary.getUri(),
                                                                                     new VocabularyImporter.ImportInput(
                                                                                             Constants.MediaType.EXCEL,
                                                                                             Environment.loadFile(
                                                                                                     "data/import-simple-de.xlsx"))
                                                    ));
        assertEquals("error.vocabulary.import.excel.missingIdentifierOrLabel", ex.getMessageId());
        verify(termService, never()).update(any());
    }

    @Test
    void importSkipsUnknownParentDeclaration() throws Exception {
        initVocabularyResolution();
        final Workbook input = new XSSFWorkbook(Environment.loadFile("template/termit-import.xlsx"));
        final Sheet sheet = input.getSheet("English");
        sheet.shiftColumns(0, 12, 1);
        sheet.getRow(0).createCell(0).setCellValue("Identifier");
        sheet.getRow(1).createCell(0).setCellValue("http://example.com/terms/Construction");
        sheet.getRow(1).getCell(1).setCellValue("Construction");
        sheet.getRow(1).getCell(8).setCellValue("Unknown parent");
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        input.write(bos);

        sut.importVocabulary(new VocabularyImporter.ImportConfiguration(false,
                                                                        vocabulary.getUri(),
                                                                        prePersist),
                             new VocabularyImporter.ImportInput(
                                     Constants.MediaType.EXCEL,
                                     new ByteArrayInputStream(
                                             bos.toByteArray())));
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        assertTrue(Utils.emptyIfNull(captor.getValue().getParentTerms()).isEmpty());
    }

    @Test
    void importSupportsReferencingParentFromVocabularyImportedByTargetVocabulary() {
        initVocabularyResolution();
        final Vocabulary anotherVocabulary = new Vocabulary(URI.create("http://example.com/another-vocabulary"));
        vocabulary.setImportedVocabularies(Set.of(anotherVocabulary.getUri()));
        final Term referencedParent = new Term(URI.create("http://example.com/another-vocabulary/term/parent"));
        referencedParent.setVocabulary(anotherVocabulary.getUri());
        referencedParent.setGlossary(Generator.generateUri());
        when(termService.findDetached(referencedParent.getUri())).thenReturn(Optional.of(referencedParent));

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-external-parents-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        final Term resultTerm = captor.getValue();
        assertThat(resultTerm.getExternalParentTerms(), hasItem(referencedParent));
    }

    @Test
    void importThrowsReferencedTermInUnrelatedVocabularyExceptionWhenReferencedExternalParentIsFromUnrelatedVocabulary() {
        when(vocabularyDao.exists(vocabulary.getUri())).thenReturn(true);
        when(vocabularyDao.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Vocabulary anotherVocabulary = new Vocabulary(URI.create("http://example.com/another-vocabulary"));
        final Term referencedParent = new Term(URI.create("http://example.com/another-vocabulary/term/parent"));
        referencedParent.setVocabulary(anotherVocabulary.getUri());
        referencedParent.setGlossary(Generator.generateUri());
        when(termService.findDetached(referencedParent.getUri())).thenReturn(Optional.of(referencedParent));

        final ReferencedTermInUnrelatedVocabularyException ex = assertThrows(
                ReferencedTermInUnrelatedVocabularyException.class, () -> sut.importVocabulary(
                        new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                        new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                           Environment.loadFile(
                                                                   "data/import-with-external-parents-en.xlsx"))));
        assertEquals("error.vocabulary.import.excel.externalParentUnrelatedVocabulary", ex.getMessageId());
    }

    @Test
    void importSupportsExternalParentWithSameLabelAsImportedTerm() {
        initVocabularyResolution();
        final Vocabulary anotherVocabulary = new Vocabulary(URI.create("http://example.com/another-vocabulary"));
        vocabulary.setImportedVocabularies(Set.of(anotherVocabulary.getUri()));
        final Term referencedParent = new Term(URI.create("http://example.com/another-vocabulary/term/parent"));
        referencedParent.setVocabulary(anotherVocabulary.getUri());
        referencedParent.setGlossary(Generator.generateUri());
        referencedParent.setLabel(MultilingualString.create("Building", "en"));
        when(termService.findDetached(referencedParent.getUri())).thenReturn(Optional.of(referencedParent));

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-external-parents-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(captor.capture(), eq(vocabulary));
        final Term resultTerm = captor.getValue();
        assertThat(resultTerm.getExternalParentTerms(), hasItem(referencedParent));
    }

    @Test
    void importSupportsMultipleColumnsPerPluralAttribute() {
        initVocabularyResolution();
        when(termService.exists(any())).thenReturn(false);
        when(termService.exists(URI.create("http://example.com/another-vocabulary/terms/relatedMatch"))).thenReturn(
                true);
        when(termService.exists(
                URI.create("http://example.com/another-vocabulary/terms/anotherRelatedMatch"))).thenReturn(true);

        final Vocabulary result = sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-multicolumn-en.xlsx")));
        assertEquals(vocabulary, result);
        final ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(termService).addRootTermToVocabulary(termCaptor.capture(), eq(vocabulary));
        final ArgumentCaptor<Collection<Quad>> quadsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataDao).insertRawData(quadsCaptor.capture());
        assertEquals(2, quadsCaptor.getValue().size());
        assertEquals(List.of(new Quad(termCaptor.getAllValues().get(0).getUri(), URI.create(SKOS.RELATED_MATCH),
                                      URI.create("http://example.com/another-vocabulary/terms/relatedMatch"),
                                      vocabulary.getUri()),
                             (new Quad(termCaptor.getAllValues().get(0).getUri(), URI.create(SKOS.RELATED_MATCH),
                                       URI.create("http://example.com/another-vocabulary/terms/anotherRelatedMatch"),
                                       vocabulary.getUri()))),
                     quadsCaptor.getValue());
    }

    @Test
    void importNotifiesExternalTermsReferencedByImportedTerms() {
        initVocabularyResolution();
        final Vocabulary anotherVocabulary = new Vocabulary(URI.create("http://example.com/another-vocabulary"));
        vocabulary.setImportedVocabularies(Set.of(anotherVocabulary.getUri()));
        final Term referencedParent = new Term(URI.create("http://example.com/another-vocabulary/term/parent"));
        referencedParent.setVocabulary(anotherVocabulary.getUri());
        referencedParent.setGlossary(Generator.generateUri());
        when(termService.findDetached(referencedParent.getUri())).thenReturn(Optional.of(referencedParent));

        sut.importVocabulary(
                new VocabularyImporter.ImportConfiguration(false, vocabulary.getUri(), prePersist),
                new VocabularyImporter.ImportInput(Constants.MediaType.EXCEL,
                                                   Environment.loadFile(
                                                           "data/import-with-external-parents-en.xlsx")));
        final ArgumentCaptor<TermReferencesUpdatedEvent> captor = ArgumentCaptor.forClass(
                TermReferencesUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream()
                         .anyMatch(evt -> SKOS.NARROWER.equals(evt.getProperty()) && referencedParent.getUri()
                                                                                                     .equals(evt.getTermUri())));
    }
}

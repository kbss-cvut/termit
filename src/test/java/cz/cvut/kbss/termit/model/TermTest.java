/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TermTest {

    @Test
    void toCsvOutputsAllColumnsEvenIfNotAllAttributesArePresent() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        final String result = term.toCsv();
        int count = 1;
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) == ',') {
                count++;
            }
        }
        assertEquals(Term.EXPORT_COLUMNS.size(), count);
    }

    @Test
    void toCsvGeneratesStringContainingIriLabelDefinitionAndComment() {
        final Term term = Generator.generateTermWithId();
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(4));
        assertEquals(term.getUri().toString(), items[0]);
        term.getLabel().getValue().values().forEach(v -> assertThat(items[1], containsString(v)));
        term.getDefinition().getValue().values().forEach(v -> assertThat(items[4], containsString(v)));
        term.getDescription().getValue().values().forEach(v -> assertThat(items[5], containsString(v)));
    }

    @Test
    void toCsvPutsCommentInQuotesToEscapeCommas() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(MultilingualString.create("Comment, with a comma", Environment.LANGUAGE));
        final String result = term.toCsv();
        assertThat(result, containsString("\"" + term.getDescription().get(Environment.LANGUAGE) + "\""));
    }

    @Test
    void toCsvExportsAltLabelsDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setAltLabels(new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                MultilingualString.create("Construction", Environment.LANGUAGE))));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertEquals(items.length, 14);
        final String list = items[2];
        assertTrue(list.matches(".+;.+"));
        term.getAltLabels().forEach(t -> assertTrue(list.contains(t.get())));
    }

    @Test
    void toCsvExportsHiddenLabelsDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setHiddenLabels(
                new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                        MultilingualString.create("Construction", Environment.LANGUAGE))));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertEquals(items.length, 14);
        final String list = items[3];
        assertTrue(list.matches(".+;.+"));
        term.getHiddenLabels().forEach(t -> assertTrue(list.contains(t.get())));
    }

    @Test
    void toCsvExportsTypesDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setTypes(new LinkedHashSet<>(Arrays.asList(Vocabulary.s_c_object, Vocabulary.s_c_entity)));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(5));
        final String types = items[6];
        assertTrue(types.matches(".+;.+"));
        term.getTypes().forEach(t -> assertTrue(types.contains(t)));
    }

    @Test
    void toCsvExportsSourcesDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(6));
        final String sources = items[7];
        assertTrue(sources.matches(".+;.+"));
        term.getSources().forEach(t -> assertTrue(sources.contains(t)));
    }

    @Test
    void toCsvExportsParentTermIrisDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(7));
        final String parentTerms = items[8];
        assertTrue(parentTerms.matches(".+;.+"));
        term.getParentTerms().forEach(t -> assertTrue(parentTerms.contains(t.getUri().toString())));
    }

    @Test
    void toCsvExportsSubTermIrisDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(8));
        final String subTerms = items[9];
        assertTrue(subTerms.matches(".+;.+"));
        term.getSubTerms().forEach(t -> assertTrue(subTerms.contains(t.getUri().toString())));
    }

    private TermInfo generateTermInfo() {
        final TermInfo ti = new TermInfo(Generator.generateUri());
        ti.setLabel(MultilingualString.create("Term " + Generator.randomInt(), Environment.LANGUAGE));
        ti.setVocabulary(Generator.generateUri());
        return ti;
    }

    @Test
    void toExcelExportsTermToExcelRow() {
        final Term term = Generator.generateTermWithId();
        term.setTypes(Collections.singleton(Vocabulary.s_c_object));
        term.setAltLabels(new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                MultilingualString.create("Construction", Environment.LANGUAGE))));
        term.setHiddenLabels(
                new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                        MultilingualString.create("Construction", Environment.LANGUAGE))));
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        term.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        term.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        final XSSFRow row = generateExcel();
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getLabel().getValue().values()
            .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertTrue(row.getCell(2).getStringCellValue().matches(".+;.+"));
        term.getAltLabels().forEach(s -> assertTrue(row.getCell(2).getStringCellValue().contains(s.get())));
        assertTrue(row.getCell(3).getStringCellValue().matches(".+;.+"));
        term.getHiddenLabels().forEach(s -> assertTrue(row.getCell(3).getStringCellValue().contains(s.get())));
        term.getDefinition().getValue().values()
            .forEach(v -> assertThat(row.getCell(4).getStringCellValue(), containsString(v)));
        term.getDescription().getValue().values()
            .forEach(v -> assertThat(row.getCell(5).getStringCellValue(), containsString(v)));
        assertEquals(term.getTypes().iterator().next(), row.getCell(6).getStringCellValue());
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        term.getSources().forEach(s -> assertTrue(row.getCell(7).getStringCellValue().contains(s)));
        assertTrue(row.getCell(8).getStringCellValue().matches(".+;.+"));
        term.getParentTerms()
            .forEach(st -> assertTrue(row.getCell(8).getStringCellValue().contains(st.getUri().toString())));
        assertTrue(row.getCell(9).getStringCellValue().matches(".+;.+"));
        term.getSubTerms()
            .forEach(st -> assertTrue(row.getCell(9).getStringCellValue().contains(st.getUri().toString())));
    }

    @Test
    void toExcelHandlesEmptyOptionalAttributeValues() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setDefinition(null);
        final XSSFRow row = generateExcel();
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getLabel().getValue().values()
            .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertEquals(Term.EXPORT_COLUMNS.size(), row.getLastCellNum());
    }

    @Test
    void toExcelHandlesSkippingEmptyColumns() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final XSSFRow row = generateExcel();
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getLabel().getValue().values()
            .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        term.getSources().forEach(s -> assertTrue(row.getCell(7).getStringCellValue().contains(s)));
    }

    @Test
    void toCsvSanitizesTermUriToHandleCommas() {
        final Term term = Generator.generateTerm();
        term.setUri(URI.create(
                "http://onto.fel.cvut.cz/ontologies/slovnik/oha-togaf/pojem/koncept-katalogů,-matic-a-pohledů"));
        final String result = term.toCsv();
        assertTrue(result.startsWith("\"" + term.getUri().toString() + "\","));
    }

    @Test
    void hasParentInSameVocabularyReturnsFalseWhenTermHasNoParent() {
        final Term sut = Generator.generateTermWithId();
        assertFalse(sut.hasParentInSameVocabulary());
    }

    @Test
    void hasParentInSameVocabularyReturnsTrueWhenTermHasParentWithSameVocabulary() {
        final Term sut = Generator.generateTermWithId();
        final URI vocabularyUri = Generator.generateUri();
        sut.setGlossary(vocabularyUri);
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabularyUri);
        sut.addParentTerm(parent);

        assertTrue(sut.hasParentInSameVocabulary());
    }

    @Test
    void hasParentInSameVocabularyReturnsFalseWhenTermHasParentWithDifferentVocabulary() {
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(Generator.generateUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(Generator.generateUri());
        sut.addParentTerm(parent);

        assertFalse(sut.hasParentInSameVocabulary());
    }

    @Test
    void toCsvExportsAltLabelsInDifferentLanguages() {
        final Term sut = Generator.generateTermWithId();
        final MultilingualString altOne = MultilingualString.create("Building", "en");
        altOne.set("cs", "Budova");
        final MultilingualString altTwo = MultilingualString.create("Construction", "en");
        altTwo.set("en", "Construction");
        sut.setAltLabels(new HashSet<>(Arrays.asList(altOne, altTwo)));
        final String result = sut.toCsv();
        final String[] items = result.split(",");
        final String list = items[2];
        assertTrue(list.matches(".+;.+"));
        sut.getAltLabels().forEach(t -> t.getValue().values().forEach(v -> assertTrue(list.contains(v))));
    }

    @Test
    void toExcelHandlesNullAltLabelsAttribute() {
        final Term sut = Generator.generateTermWithId();
        final MultilingualString hiddenOne = MultilingualString.create("budova", "cs");
        final MultilingualString hiddenTwo = MultilingualString.create("budovy", "cs");
        sut.setAltLabels(null);
        sut.setHiddenLabels(new HashSet<>(Arrays.asList(hiddenOne, hiddenTwo)));

        final XSSFRow row = generateExcel();
        sut.toExcel(row);
        assertEquals(sut.getUri().toString(), row.getCell(0).getStringCellValue());
        sut.getHiddenLabels().forEach(ms -> ms.getValue().values()
                                              .forEach(v -> assertThat(row.getCell(3).getStringCellValue(),
                                                      containsString(v))));
    }

    private XSSFRow generateExcel() {
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = wb.createSheet("test");
        return sheet.createRow(0);
    }

    @Test
    void consolidateInferredCopiesInverseRelatedTermsToRelated() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));
        final int originalRelatedSize = sut.getRelated().size();

        sut.consolidateInferred();
        assertEquals(originalRelatedSize + sut.getInverseRelated().size(), sut.getRelated().size());
        sut.getInverseRelated().forEach(ti -> assertThat(sut.getRelated(), hasItem(ti)));
    }

    @Test
    void consolidateInferredCopiesInverseRelatedMatchTermsToRelatedMatch() {
        final Term sut = Generator.generateTermWithId();
        sut.setRelatedMatch(IntStream.range(0, 5)
                                     .mapToObj(i -> new TermInfo(Generator.generateTermWithId(Generator.generateUri())))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5)
                                            .mapToObj(i -> new TermInfo(Generator.generateTermWithId(Generator.generateUri())))
                                            .collect(Collectors.toSet()));
        final int originalRelatedMatchSize = sut.getRelatedMatch().size();

        sut.consolidateInferred();
        assertEquals(originalRelatedMatchSize + sut.getInverseRelatedMatch().size(), sut.getRelatedMatch().size());
        sut.getInverseRelatedMatch().forEach(ti -> assertThat(sut.getRelatedMatch(), hasItem(ti)));
    }

    @Test
    void consolidateInferredCopiesInverseExactMatchTermsToExactMatch() {
        final Term sut = Generator.generateTermWithId();
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator
                .generateTermWithId(Generator.generateUri()))).collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator
                .generateTermWithId(Generator.generateUri()))).collect(Collectors.toSet()));
        final int originalExactMatchSize = sut.getExactMatchTerms().size();

        sut.consolidateInferred();
        assertEquals(originalExactMatchSize + sut.getInverseExactMatchTerms().size(), sut.getExactMatchTerms().size());
        sut.getInverseExactMatchTerms().forEach(ti -> assertThat(sut.getExactMatchTerms(), hasItem(ti)));
    }

    @Test
    void toCsvIncludesRelatedAndInverseRelatedTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));
        final String result = sut.toCsv();
        final String[] items = result.split(",");
        final String related = items[10];
        assertTrue(related.matches(".+;.+"));
        sut.getRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void toCsvIncludesRelatedMatchAndInverseRelatedMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                            .collect(Collectors.toSet()));
        final String result = sut.toCsv();
        final String[] items = result.split(",");
        final String related = items[11];
        assertTrue(related.matches(".+;.+"));
        sut.getRelatedMatch().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelatedMatch().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void toCsvIncludesExactMatchAndInverseExactMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                        .collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet()));
        final String result = sut.toCsv();
        final String[] items = result.split(",");
        final String related = items[12];
        assertTrue(related.matches(".+;.+"));
        sut.getExactMatchTerms().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseExactMatchTerms().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void toExcelIncludesRelatedAndInverseRelatedTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.toExcel(row);
        final String related = row.getCell(10).getStringCellValue();
        assertTrue(related.matches(".+;.+"));
        sut.getRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void toExcelIncludesRelatedMatchAndInverseRelatedMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                            .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.toExcel(row);
        final String relatedMatch = row.getCell(11).getStringCellValue();
        assertTrue(relatedMatch.matches(".+;.+"));
        sut.getRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
        sut.getInverseRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
    }

    @Test
    void toExcelIncludesExactMatchAndInverseExactMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                        .collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.toExcel(row);
        final String exactMatch = row.getCell(12).getStringCellValue();
        assertTrue(exactMatch.matches(".+;.+"));
        sut.getExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
        sut.getInverseExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
    }

    @Test
    void addParentTermAddsSpecifiedTermToParentsWhenItIsFromSameGlossary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(vocabulary.getGlossary().getUri());
        final Term parentToAdd = Generator.generateTermWithId();
        parentToAdd.setGlossary(vocabulary.getGlossary().getUri());

        sut.addParentTerm(parentToAdd);
        assertThat(sut.getParentTerms(), hasItem(parentToAdd));
        assertThat(sut.getExternalParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void addParentTermAddsSpecifiedTermToExternalParentsWhenItIsFromDifferentGlossary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(vocabulary.getGlossary().getUri());
        final Term parentToAdd = Generator.generateTermWithId();
        parentToAdd.setGlossary(Generator.generateUri());

        sut.addParentTerm(parentToAdd);
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
        assertThat(sut.getExternalParentTerms(), hasItem(parentToAdd));
    }

    @Test
    void consolidateParentsCopiesExternalParentTermsToParentTerms() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        final Set<Term> externalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(Generator.generateUri());
            return t;
        }).collect(Collectors.toSet());
        sut.setExternalParentTerms(externalParents);

        sut.consolidateParents();
        assertThat(sut.getParentTerms(), hasItems(externalParents.toArray(new Term[0])));
    }

    @Test
    void consolidateParentsHandlesNullExternalParentTerms() {
        final Term sut = Generator.generateTermWithId();

        sut.consolidateParents();
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void splitExternalAndInternalParentsMovesParentsWithDifferentGlossaryFromParentTermsToExternalParentTerms() {
        final URI glossaryUri = Generator.generateUri();
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(glossaryUri);
        final Set<Term> externalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(Generator.generateUri());
            return t;
        }).collect(Collectors.toSet());
        final Set<Term> internalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(glossaryUri);
            return t;
        }).collect(Collectors.toSet());
        final Set<Term> allParents = new HashSet<>(externalParents);
        allParents.addAll(internalParents);
        sut.setParentTerms(allParents);

        sut.splitExternalAndInternalParents();
        assertEquals(internalParents, sut.getParentTerms());
        assertEquals(externalParents, sut.getExternalParentTerms());
    }

    @Test
    void splitExternalAndInternalParentsDoesNothingWhenTermHasNoParents() {
        final Term sut = Generator.generateTermWithId();
        sut.splitExternalAndInternalParents();
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
        assertThat(sut.getExternalParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void toExcelEnsuresNoDuplicatesInRelatedRelatedMatchAndExactMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                                .collect(Collectors.toSet());
        sut.setRelated(new HashSet<>(asserted));
        sut.setRelatedMatch(new HashSet<>(asserted));
        sut.setExactMatchTerms(new HashSet<>(asserted));
        final Set<TermInfo> inverse = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet());
        sut.setInverseRelated(new HashSet<>(inverse));
        sut.setInverseRelatedMatch(new HashSet<>(inverse));
        sut.setInverseExactMatchTerms(new HashSet<>(inverse));
        sut.consolidateInferred();

        final XSSFRow row = generateExcel();
        sut.toExcel(row);
        final String resultRelated = row.getCell(10).getStringCellValue();
        final String[] relatedIris = resultRelated.split(";");
        assertEquals(asserted.size() + inverse.size(), relatedIris.length);
        final String resultRelatedMatch = row.getCell(11).getStringCellValue();
        final String[] relatedMatchIris = resultRelatedMatch.split(";");
        assertEquals(asserted.size() + inverse.size(), relatedMatchIris.length);
        final String resultExactMatch = row.getCell(12).getStringCellValue();
        final String[] exactMatchIris = resultExactMatch.split(";");
        assertEquals(asserted.size() + inverse.size(), exactMatchIris.length);
    }

    @Test
    void toCsvEnsuresNoDuplicatesInRelatedRelatedMatchAndExactMatchTerms() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                                .collect(Collectors.toSet());
        sut.setRelated(new HashSet<>(asserted));
        sut.setRelatedMatch(new HashSet<>(asserted));
        sut.setExactMatchTerms(new HashSet<>(asserted));
        final Set<TermInfo> inverse = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet());
        sut.setInverseRelated(new HashSet<>(inverse));
        sut.setInverseRelatedMatch(new HashSet<>(inverse));
        sut.setInverseExactMatchTerms(new HashSet<>(inverse));
        sut.consolidateInferred();

        final String result = sut.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(7));
        final String resultRelated = items[10];
        final String[] relatedIris = resultRelated.split(";");
        assertEquals(asserted.size() + inverse.size(), relatedIris.length);
        final String resultRelatedMatch = items[11];
        final String[] relatedMatchIris = resultRelatedMatch.split(";");
        assertEquals(asserted.size() + inverse.size(), relatedMatchIris.length);
        final String resultExactMatch = items[12];
        final String[] exactMatchIris = resultExactMatch.split(";");
        assertEquals(asserted.size() + inverse.size(), exactMatchIris.length);
    }

    @Test
    void isSnapshotReturnsTrueWhenInstanceHasSnapshotType() {
        final Term original = Generator.generateTermWithId();
        final Term snapshot = Generator.generateTermWithId();
        snapshot.addType(Vocabulary.s_c_verze_pojmu);
        assertFalse(original.isSnapshot());
        assertTrue(snapshot.isSnapshot());
    }
}

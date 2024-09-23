/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelTermExporterTest {

    private final ExcelTermExporter sut = new ExcelTermExporter(new HashMap<>(), Environment.LANGUAGE);

    @Test
    void exportExportsTermToExcelRow() {
        final Term term = Generator.generateTermWithId();
        term.setTypes(Collections.singleton(Vocabulary.s_c_object));
        term.setAltLabels(new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                                      MultilingualString.create("Construction",
                                                                                Environment.LANGUAGE))));
        term.setHiddenLabels(
                new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                            MultilingualString.create("Construction", Environment.LANGUAGE))));
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        term.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        term.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        term.setNotations(Collections.singleton("A"));
        term.setExamples(Collections.singleton(MultilingualString.create("hospital", Environment.LANGUAGE)));
        final XSSFRow row = generateExcel();
        sut.export(term, row);
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
        term.getSources().forEach(s -> assertThat(row.getCell(7).getStringCellValue(), containsString(s)));
        assertTrue(row.getCell(8).getStringCellValue().matches(".+;.+"));
        term.getParentTerms()
            .forEach(st -> assertThat(row.getCell(8).getStringCellValue(), containsString(st.getUri().toString())));
        assertTrue(row.getCell(9).getStringCellValue().matches(".+;.+"));
        term.getSubTerms()
            .forEach(st -> assertThat(row.getCell(9).getStringCellValue(), containsString(st.getUri().toString())));
        term.getNotations().forEach(n -> assertThat(row.getCell(14).getStringCellValue(), containsString(n)));
        term.getExamples().forEach(ms -> assertThat(row.getCell(15).getStringCellValue(), containsString(ms.get())));
    }

    private static TermInfo generateTermInfo() {
        final TermInfo ti = new TermInfo(Generator.generateUri());
        ti.setLabel(MultilingualString.create("Term " + Generator.randomInt(), Environment.LANGUAGE));
        ti.setVocabulary(Generator.generateUri());
        return ti;
    }

    private static XSSFRow generateExcel() {
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = wb.createSheet("test");
        return sheet.createRow(0);
    }

    @Test
    void exportHandlesEmptyOptionalAttributeValues() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setDefinition(null);
        final XSSFRow row = generateExcel();
        sut.export(term, row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getLabel().getValue().values()
            .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
    }

    @Test
    void exportHandlesSkippingEmptyColumns() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final XSSFRow row = generateExcel();
        sut.export(term, row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getLabel().getValue().values()
            .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        term.getSources().forEach(s -> assertTrue(row.getCell(7).getStringCellValue().contains(s)));
    }

    @Test
    void exportHandlesNullAltLabelsAttribute() {
        final Term term = Generator.generateTermWithId();
        final MultilingualString hiddenOne = MultilingualString.create("building", Environment.LANGUAGE);
        final MultilingualString hiddenTwo = MultilingualString.create("buildings", Environment.LANGUAGE);
        term.setAltLabels(null);
        term.setHiddenLabels(new HashSet<>(Arrays.asList(hiddenOne, hiddenTwo)));

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        term.getHiddenLabels().forEach(ms -> ms.getValue().values()
                                               .forEach(v -> assertThat(row.getCell(3).getStringCellValue(),
                                                                        containsString(v))));
    }

    @Test
    void exportIncludesRelatedAndInverseRelatedTerms() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        term.setRelated(IntStream.range(0, 5)
                                 .mapToObj(i -> new TermInfo(Generator.generateTermWithId(term.getVocabulary())))
                                 .collect(Collectors.toSet()));
        term.setInverseRelated(IntStream.range(0, 5)
                                        .mapToObj(i -> new TermInfo(Generator.generateTermWithId(term.getVocabulary())))
                                        .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        final String related = row.getCell(10).getStringCellValue();
        assertTrue(related.matches(".+;.+"));
        term.getRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        term.getInverseRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void exportIncludesRelatedMatchAndInverseRelatedMatchTerms() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        term.setRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                      .collect(Collectors.toSet()));
        term.setInverseRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                             .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        final String relatedMatch = row.getCell(11).getStringCellValue();
        assertTrue(relatedMatch.matches(".+;.+"));
        term.getRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
        term.getInverseRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
    }

    @Test
    void exportIncludesExactMatchAndInverseExactMatchTerms() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        term.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                         .collect(Collectors.toSet()));
        term.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                                .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        final String exactMatch = row.getCell(12).getStringCellValue();
        assertTrue(exactMatch.matches(".+;.+"));
        term.getExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
        term.getInverseExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
    }

    @Test
    void exportEnsuresNoDuplicatesInRelatedRelatedMatchAndExactMatchTerms() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(Generator.generateUri());
        final Set<TermInfo> asserted = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                                .collect(Collectors.toSet());
        term.setRelated(new HashSet<>(asserted));
        term.setRelatedMatch(new HashSet<>(asserted));
        term.setExactMatchTerms(new HashSet<>(asserted));
        final Set<TermInfo> inverse = IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet());
        term.setInverseRelated(new HashSet<>(inverse));
        term.setInverseRelatedMatch(new HashSet<>(inverse));
        term.setInverseExactMatchTerms(new HashSet<>(inverse));
        term.consolidateInferred();

        final XSSFRow row = generateExcel();
        sut.export(term, row);
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
    void exportExportsSpecifiedLanguageOfMultilingualString() {
        final String lang = "cs";
        final ExcelTermExporter sut = new ExcelTermExporter(new HashMap<>(), lang);
        final Term term = Generator.generateMultiLingualTerm(Environment.LANGUAGE, lang);
        final XSSFRow row = generateExcel();
        sut.export(term, row);
        final String label = row.getCell(1).getStringCellValue();
        assertEquals(term.getLabel().get(lang), label);
        final String definition = row.getCell(4).getStringCellValue();
        assertEquals(term.getDefinition().get(lang), definition);
        final String description = row.getCell(5).getStringCellValue();
        assertEquals(term.getDescription().get(lang), description);
    }

    @Test
    void exportRemovesMarkdownMarkupFromDefinitionAndScopeNote() {
        final Term term = Generator.generateTermWithId();
        final String markdown = "# This is a headline\n" +
                "**This is bold text** and _this is italics_";
        final String text = "This is a headline\n\nThis is bold text and this is italics";
        term.getDefinition().set(Environment.LANGUAGE, markdown);
        term.getDescription().set(Environment.LANGUAGE, markdown);

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        assertThat(row.getCell(4).getStringCellValue(), containsString(text));
        assertThat(row.getCell(5).getStringCellValue(), containsString(text));
    }

    @Test
    void exportReplacesFullIrisWithPrefixedWhenPossible() {
        final String prefix = "test";
        final String namespace = Environment.BASE_URI + "/";
        final URI vocabularyUri = Generator.generateUri();
        final Term term = Generator.generateTermWithId(vocabularyUri);
        term.addParentTerm(Generator.generateTermWithId(vocabularyUri));
        term.setSubTerms(Collections.singleton(new TermInfo(Generator.generateTermWithId(vocabularyUri))));
        final Map<URI, PrefixDeclaration> prefixes = Collections.singletonMap(vocabularyUri,
                                                                              new PrefixDeclaration(prefix, namespace));
        final ExcelTermExporter sut = new ExcelTermExporter(prefixes, Environment.LANGUAGE);

        final XSSFRow row = generateExcel();
        sut.export(term, row);
        assertThat(row.getCell(0).getStringCellValue(), containsString(
                prefix + PrefixDeclaration.SEPARATOR + IdentifierResolver.extractIdentifierFragment(term.getUri())));
        term.getParentTerms().forEach(pt -> assertThat(row.getCell(8).getStringCellValue(), containsString(
                prefix + PrefixDeclaration.SEPARATOR + IdentifierResolver.extractIdentifierFragment(pt.getUri()))));
        term.getSubTerms().forEach(st -> assertThat(row.getCell(9).getStringCellValue(), containsString(
                prefix + PrefixDeclaration.SEPARATOR + IdentifierResolver.extractIdentifierFragment(st.getUri()))));
    }
}

/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
        assertEquals(term.getLabel(), items[1]);
        assertEquals(term.getDefinition(), items[2]);
        assertEquals(term.getDescription(), items[3]);
    }

    @Test
    void toCsvPutsCommentInQuotesToEscapeCommas() {
        final Term term = Generator.generateTermWithId();
        term.setDescription("Comment, with a comma");
        final String result = term.toCsv();
        assertThat(result, containsString("\"" + term.getDescription() + "\""));
    }

    @Test
    void toCsvExportsTypesDelimitedBySemicolons() {
        final Term term = Generator.generateTermWithId();
        term.setTypes(new LinkedHashSet<>(Arrays.asList(Vocabulary.s_c_object, Vocabulary.s_c_entity)));
        final String result = term.toCsv();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(5));
        final String types = items[4];
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
        final String sources = items[5];
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
        final String parentTerms = items[6];
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
        final String subTerms = items[7];
        assertTrue(subTerms.matches(".+;.+"));
        term.getSubTerms().forEach(t -> assertTrue(subTerms.contains(t.getUri().toString())));
    }

    private TermInfo generateTermInfo() {
        final TermInfo ti = new TermInfo();
        ti.setUri(Generator.generateUri());
        ti.setLabel("Term" + Generator.randomInt());
        return ti;
    }

    @Test
    void toExcelExportsTermToExcelRow() {
        final Term term = Generator.generateTermWithId();
        term.setTypes(Collections.singleton(Vocabulary.s_c_object));
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        term.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        term.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = wb.createSheet("test");
        final XSSFRow row = sheet.createRow(0);
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        assertEquals(term.getLabel(), row.getCell(1).getStringCellValue());
        assertEquals(term.getDefinition(), row.getCell(2).getStringCellValue());
        assertEquals(term.getDescription(), row.getCell(3).getStringCellValue());
        assertEquals(term.getTypes().iterator().next(), row.getCell(4).getStringCellValue());
        assertTrue(row.getCell(5).getStringCellValue().matches(".+;.+"));
        term.getSources().forEach(s -> assertTrue(row.getCell(5).getStringCellValue().contains(s)));
        assertTrue(row.getCell(6).getStringCellValue().matches(".+;.+"));
        term.getParentTerms()
            .forEach(st -> assertTrue(row.getCell(6).getStringCellValue().contains(st.getUri().toString())));
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        term.getSubTerms()
            .forEach(st -> assertTrue(row.getCell(7).getStringCellValue().contains(st.getUri().toString())));
    }

    @Test
    void toExcelHandlesEmptyOptionalAttributeValues() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setDefinition(null);
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = wb.createSheet("test");
        final XSSFRow row = sheet.createRow(0);
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        assertEquals(term.getLabel(), row.getCell(1).getStringCellValue());
        assertEquals(9, row.getLastCellNum());
    }

    @Test
    void toExcelHandlesSkippingEmptyColumns() {
        final Term term = Generator.generateTermWithId();
        term.setDescription(null);
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet sheet = wb.createSheet("test");
        final XSSFRow row = sheet.createRow(0);
        term.toExcel(row);
        assertEquals(term.getUri().toString(), row.getCell(0).getStringCellValue());
        assertEquals(term.getLabel(), row.getCell(1).getStringCellValue());
        assertTrue(row.getCell(5).getStringCellValue().matches(".+;.+"));
        term.getSources().forEach(s -> assertTrue(row.getCell(5).getStringCellValue().contains(s)));
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
}

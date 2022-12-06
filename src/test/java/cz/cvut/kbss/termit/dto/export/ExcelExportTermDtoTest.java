package cz.cvut.kbss.termit.dto.export;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapper;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapperImpl;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelExportTermDtoTest {

    private static final TermDtoMapper dtoMapper = new TermDtoMapperImpl();

    @Test
    void toExcelExportsTermToExcelRow() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(Generator.generateTermWithId());
        sut.setTypes(Collections.singleton(Vocabulary.s_c_object));
        sut.setAltLabels(new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                                     MultilingualString.create("Construction",
                                                                               Environment.LANGUAGE))));
        sut.setHiddenLabels(
                new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                            MultilingualString.create("Construction", Environment.LANGUAGE))));
        sut.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        sut.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        sut.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        sut.setNotations(Collections.singleton("A"));
        sut.setExamples(Collections.singleton(MultilingualString.create("hospital", Environment.LANGUAGE)));
        final XSSFRow row = generateExcel();
        sut.export(row);
        assertEquals(sut.getUri().toString(), row.getCell(0).getStringCellValue());
        sut.getLabel().getValue().values()
           .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertTrue(row.getCell(2).getStringCellValue().matches(".+;.+"));
        sut.getAltLabels().forEach(s -> assertTrue(row.getCell(2).getStringCellValue().contains(s.get())));
        assertTrue(row.getCell(3).getStringCellValue().matches(".+;.+"));
        sut.getHiddenLabels().forEach(s -> assertTrue(row.getCell(3).getStringCellValue().contains(s.get())));
        sut.getDefinition().getValue().values()
           .forEach(v -> assertThat(row.getCell(4).getStringCellValue(), containsString(v)));
        sut.getDescription().getValue().values()
           .forEach(v -> assertThat(row.getCell(5).getStringCellValue(), containsString(v)));
        assertEquals(sut.getTypes().iterator().next(), row.getCell(6).getStringCellValue());
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        sut.getSources().forEach(s -> assertThat(row.getCell(7).getStringCellValue(), containsString(s)));
        assertTrue(row.getCell(8).getStringCellValue().matches(".+;.+"));
        sut.getParentTerms()
           .forEach(st -> assertThat(row.getCell(8).getStringCellValue(), containsString(st.getUri().toString())));
        assertTrue(row.getCell(9).getStringCellValue().matches(".+;.+"));
        sut.getSubTerms()
           .forEach(st -> assertThat(row.getCell(9).getStringCellValue(), containsString(st.getUri().toString())));
        sut.getNotations().forEach(n -> assertThat(row.getCell(14).getStringCellValue(), containsString(n)));
        sut.getExamples().forEach(ms -> assertThat(row.getCell(15).getStringCellValue(), containsString(ms.get())));
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
    void toExcelHandlesEmptyOptionalAttributeValues() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(Generator.generateTermWithId());
        sut.setDescription(null);
        sut.setDefinition(null);
        final XSSFRow row = generateExcel();
        sut.export(row);
        assertEquals(sut.getUri().toString(), row.getCell(0).getStringCellValue());
        sut.getLabel().getValue().values()
           .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
    }

    @Test
    void toExcelHandlesSkippingEmptyColumns() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(Generator.generateTermWithId());
        sut.setDescription(null);
        sut.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final XSSFRow row = generateExcel();
        sut.export(row);
        assertEquals(sut.getUri().toString(), row.getCell(0).getStringCellValue());
        sut.getLabel().getValue().values()
           .forEach(v -> assertThat(row.getCell(1).getStringCellValue(), containsString(v)));
        assertTrue(row.getCell(7).getStringCellValue().matches(".+;.+"));
        sut.getSources().forEach(s -> assertTrue(row.getCell(7).getStringCellValue().contains(s)));
    }

    @Test
    void toExcelHandlesNullAltLabelsAttribute() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(Generator.generateTermWithId());
        final MultilingualString hiddenOne = MultilingualString.create("budova", "cs");
        final MultilingualString hiddenTwo = MultilingualString.create("budovy", "cs");
        sut.setAltLabels(null);
        sut.setHiddenLabels(new HashSet<>(Arrays.asList(hiddenOne, hiddenTwo)));

        final XSSFRow row = generateExcel();
        sut.export(row);
        assertEquals(sut.getUri().toString(), row.getCell(0).getStringCellValue());
        sut.getHiddenLabels().forEach(ms -> ms.getValue().values()
                                              .forEach(v -> assertThat(row.getCell(3).getStringCellValue(),
                                                                       containsString(v))));
    }

    @Test
    void toExcelIncludesRelatedAndInverseRelatedTerms() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(row);
        final String related = row.getCell(10).getStringCellValue();
        assertTrue(related.matches(".+;.+"));
        sut.getRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void toExcelIncludesRelatedMatchAndInverseRelatedMatchTerms() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                            .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(row);
        final String relatedMatch = row.getCell(11).getStringCellValue();
        assertTrue(relatedMatch.matches(".+;.+"));
        sut.getRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
        sut.getInverseRelatedMatch().forEach(t -> assertTrue(relatedMatch.contains(t.getUri().toString())));
    }

    @Test
    void toExcelIncludesExactMatchAndInverseExactMatchTerms() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                        .collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet()));

        final XSSFRow row = generateExcel();
        sut.export(row);
        final String exactMatch = row.getCell(12).getStringCellValue();
        assertTrue(exactMatch.matches(".+;.+"));
        sut.getExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
        sut.getInverseExactMatchTerms().forEach(t -> assertTrue(exactMatch.contains(t.getUri().toString())));
    }

    @Test
    void toExcelEnsuresNoDuplicatesInRelatedRelatedMatchAndExactMatchTerms() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
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
        sut.export(row);
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
    void toExcelExportsMultilingualAttributesAsValuesWithLanguageInParentheses() {
        final ExcelExportTermDto sut = dtoMapper.termToExcelExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        final XSSFRow row = generateExcel();
        sut.export(row);
        final String label = row.getCell(1).getStringCellValue();
        assertTrue(label.matches(".+;.+"));
        sut.getLabel().getValue().forEach((lang, value) -> assertThat(label, containsString(value + "(" + lang + ")")));
        final String definition = row.getCell(4).getStringCellValue();
        assertTrue(definition.matches(".+;.+"));
        sut.getDefinition().getValue()
           .forEach((lang, value) -> assertThat(definition, containsString(value + "(" + lang + ")")));
        final String description = row.getCell(5).getStringCellValue();
        assertTrue(description.matches(".+;.+"));
        sut.getDescription().getValue()
           .forEach((lang, value) -> assertThat(description, containsString(value + "(" + lang + ")")));
    }
}

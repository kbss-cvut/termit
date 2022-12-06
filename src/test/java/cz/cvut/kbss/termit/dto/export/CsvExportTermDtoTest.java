package cz.cvut.kbss.termit.dto.export;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapper;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapperImpl;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExportTermDtoTest {

    private static final TermDtoMapper dtoMapper = new TermDtoMapperImpl();

    @Test
    void exportOutputsAllColumnsEvenIfNotAllAttributesArePresent() {
        final CsvExportTermDto term = generate();
        term.setDescription(null);
        final String result = term.export();
        int count = 1;
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) == ',') {
                count++;
            }
        }
        assertEquals(TabularTermExportUtils.EXPORT_COLUMNS.size(), count);
    }

    private static CsvExportTermDto generate() {
        return dtoMapper.termToCsvExportDto(Generator.generateTermWithId());
    }

    @Test
    void exportGeneratesStringContainingIriLabelDefinitionAndComment() {
        final CsvExportTermDto term = generate();
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(4));
        assertEquals(term.getUri().toString(), items[0]);
        term.getLabel().getValue().values().forEach(v -> assertThat(items[1], containsString(v)));
        term.getDefinition().getValue().values().forEach(v -> assertThat(items[4], containsString(v)));
        term.getDescription().getValue().values().forEach(v -> assertThat(items[5], containsString(v)));
    }

    @Test
    void exportPutsCommentInQuotesToEscapeCommas() {
        final CsvExportTermDto term = generate();
        term.setDescription(MultilingualString.create("Comment, with a comma", Environment.LANGUAGE));
        final String result = term.export();
        assertThat(result, containsString(
                "\"" + term.getDescription().get(Environment.LANGUAGE) + "\"" + "(" + Environment.LANGUAGE + ")"));
    }

    @Test
    void exportExportsAltLabelsDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setAltLabels(new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                                      MultilingualString.create("Construction",
                                                                                Environment.LANGUAGE))));
        final String result = term.export();
        final String[] items = result.split(",");
        assertEquals(items.length, 14);
        final String list = items[2];
        assertTrue(list.matches(".+;.+"));
        term.getAltLabels().forEach(t -> assertThat(list, containsString(t.get())));
    }

    @Test
    void exportExportsHiddenLabelsDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setHiddenLabels(
                new HashSet<>(Arrays.asList(MultilingualString.create("Building", Environment.LANGUAGE),
                                            MultilingualString.create("Construction", Environment.LANGUAGE))));
        final String result = term.export();
        final String[] items = result.split(",");
        assertEquals(items.length, 14);
        final String list = items[3];
        assertTrue(list.matches(".+;.+"));
        term.getHiddenLabels().forEach(t -> assertTrue(list.contains(t.get())));
    }

    @Test
    void exportExportsTypesDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setTypes(new LinkedHashSet<>(Arrays.asList(Vocabulary.s_c_object, Vocabulary.s_c_entity)));
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(7));
        final String types = items[6];
        assertTrue(types.matches(".+;.+"));
        term.getTypes().forEach(t -> assertTrue(types.contains(t)));
    }

    @Test
    void exportExportsSourcesDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setSources(new LinkedHashSet<>(
                Arrays.asList(Generator.generateUri().toString(), "PSP/c-1/p-2/b-c", "PSP/c-1/p-2/b-f")));
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(8));
        final String sources = items[7];
        assertTrue(sources.matches(".+;.+"));
        term.getSources().forEach(t -> assertTrue(sources.contains(t)));
    }

    @Test
    void exportExportsParentTermIrisDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setParentTerms(new HashSet<>(Generator.generateTermsWithIds(5)));
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(9));
        final String parentTerms = items[8];
        assertTrue(parentTerms.matches(".+;.+"));
        term.getParentTerms().forEach(t -> assertTrue(parentTerms.contains(t.getUri().toString())));
    }

    @Test
    void exportExportsSubTermIrisDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setSubTerms(IntStream.range(0, 5).mapToObj(i -> generateTermInfo()).collect(Collectors.toSet()));
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(10));
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
    void exportExportExportsSkosNotationAndExamplesDelimitedBySemicolons() {
        final CsvExportTermDto term = generate();
        term.setNotations(Collections.singleton("A"));
        term.setExamples(new HashSet<>());
        term.getExamples().add(MultilingualString.create("hospital", Environment.LANGUAGE));
        term.getExamples().add(MultilingualString.create("cottage", Environment.LANGUAGE));
        final String result = term.export();
        final String[] items = result.split(",");
        assertThat(items.length, greaterThanOrEqualTo(15));
        final String notation = items[14];
        term.getNotations().forEach(n -> assertThat(notation, containsString(n)));
        final String examples = items[15];
        term.getExamples().forEach(ms -> ms.getValue().forEach(
                (key, value) -> assertThat(examples, containsString(value + "(" + key + ")"))));
        assertTrue(examples.matches(".+;.+"));
    }

    @Test
    void exportSanitizesTermUriToHandleCommas() {
        final CsvExportTermDto term = generate();
        term.setUri(URI.create(
                "http://onto.fel.cvut.cz/ontologies/slovnik/oha-togaf/pojem/koncept-katalogů,-matic-a-pohledů"));
        final String result = term.export();
        assertTrue(result.startsWith("\"" + term.getUri().toString() + "\","));
    }

    @Test
    void exportExportsAltLabelsInDifferentLanguages() {
        final CsvExportTermDto sut = generate();
        final MultilingualString altOne = MultilingualString.create("Building", "en");
        altOne.set("cs", "Budova");
        final MultilingualString altTwo = MultilingualString.create("Construction", "en");
        altTwo.set("en", "Construction");
        sut.setAltLabels(new HashSet<>(Arrays.asList(altOne, altTwo)));
        final String result = sut.export();
        final String[] items = result.split(",");
        final String list = items[2];
        assertTrue(list.matches(".+;.+"));
        sut.getAltLabels().forEach(t -> t.getValue().values().forEach(v -> assertTrue(list.contains(v))));
    }

    @Test
    void exportIncludesRelatedAndInverseRelatedTerms() {
        final CsvExportTermDto sut = dtoMapper.termToCsvExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));
        final String result = sut.export();
        final String[] items = result.split(",");
        final String related = items[10];
        assertTrue(related.matches(".+;.+"));
        sut.getRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelated().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void exportIncludesRelatedMatchAndInverseRelatedMatchTerms() {
        final CsvExportTermDto sut = dtoMapper.termToCsvExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                            .collect(Collectors.toSet()));
        final String result = sut.export();
        final String[] items = result.split(",");
        final String related = items[11];
        assertTrue(related.matches(".+;.+"));
        sut.getRelatedMatch().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseRelatedMatch().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void exportIncludesExactMatchAndInverseExactMatchTerms() {
        final CsvExportTermDto sut = dtoMapper.termToCsvExportDto(
                Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs"));
        sut.setVocabulary(Generator.generateUri());
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                        .collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator.generateTermWithId()))
                                               .collect(Collectors.toSet()));
        final String result = sut.export();
        final String[] items = result.split(",");
        final String related = items[12];
        assertTrue(related.matches(".+;.+"));
        sut.getExactMatchTerms().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
        sut.getInverseExactMatchTerms().forEach(t -> assertTrue(related.contains(t.getUri().toString())));
    }

    @Test
    void exportEnsuresNoDuplicatesInRelatedRelatedMatchAndExactMatchTerms() {
        final CsvExportTermDto sut = dtoMapper.termToCsvExportDto(
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

        final String result = sut.export();
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
    void exportRemovesMarkdownMarkupFromDefinitionAndScopeNote() {
        final CsvExportTermDto sut = generate();
        final String markdown = "# This is a headline\n" +
                "**This is bold text** and _this is italics_";
        final String text = "This is a headline\n\nThis is bold text and this is italics";
        sut.getDefinition().set(Environment.LANGUAGE, markdown);
        sut.getDescription().set(Environment.LANGUAGE, markdown);

        final String result = sut.export();
        final String[] items = result.split(",");
        assertThat(items[4], containsString(text));
        assertThat(items[5], containsString(text));
    }
}

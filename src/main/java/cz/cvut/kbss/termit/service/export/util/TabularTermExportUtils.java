package cz.cvut.kbss.termit.service.export.util;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.CsvUtils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TabularTermExportUtils {

    /**
     * Names of columns used in term export to a tabular structure.
     */
    public static final List<String> EXPORT_COLUMNS = List.of("IRI", "Label", "Synonyms", "Search strings",
                                                              "Definition", "Scope note", "Type", "Source",
                                                              "Parent terms", "Sub terms", "Related terms",
                                                              "Related match terms", "Exact matches", "Status",
                                                              "Notation", "Example", "References"
    );

    /**
     * Delimiter of a joined list of strings.
     */
    public static final String STRING_DELIMITER = ";";

    private TabularTermExportUtils() {
        throw new AssertionError();
    }

    public static String draftToStatus(Term t) {
        return t.isDraft() ? "DRAFT" : "CONFIRMED";
    }

    /**
     * Simple extractor of TermInfo identifier to string.
     *
     * @param ti TermInfo instance
     * @return String identifier of the argument
     */
    public static String termInfoStringIri(TermInfo ti) {
        assert ti != null;
        return ti.getUri().toString();
    }

    /**
     * Transforms the specified {@link MultilingualString} to a single string where individual translations are
     * separated by a predefined delimiter.
     * <p>
     * The translations are added to the result in the following form: {@literal translation(language)}.
     *
     * @param str            Multilingual string to transform
     * @param preProcessor   Function to apply to every translation before it is added to the result
     * @param sanitizeCommas Whether to sanitize commas in the string content
     * @return A single string containing all translations from the argument
     */
    public static String exportMultilingualString(MultilingualString str, Function<String, String> preProcessor,
                                                  boolean sanitizeCommas) {
        if (str == null) {
            return "";
        }
        return String.join(STRING_DELIMITER,
                           str.getValue().entrySet().stream()
                              .map(e -> (sanitizeCommas ? CsvUtils.sanitizeString(preProcessor.apply(e.getValue())) :
                                         preProcessor.apply(e.getValue())) + "(" + e.getKey() + ")")
                              .collect(Collectors.toSet()));
    }
}

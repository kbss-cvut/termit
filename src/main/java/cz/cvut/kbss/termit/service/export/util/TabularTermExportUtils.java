package cz.cvut.kbss.termit.service.export.util;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.CsvUtils;

import java.util.function.Function;
import java.util.stream.Collectors;

public class TabularTermExportUtils {

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

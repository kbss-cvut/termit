package cz.cvut.kbss.termit.dto.export;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.CsvUtils;

import java.util.Collection;
import java.util.List;
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

    private TabularTermExportUtils() {
        throw new AssertionError();
    }

    static String draftToStatus(Term t) {
        return t.isDraft() ? "DRAFT" : "CONFIRMED";
    }

    static String termInfoStringIri(TermInfo ti) {
        assert ti != null;
        return ti.getUri().toString();
    }

    static String exportMultilingualString(MultilingualString str, boolean sanitizeCommas) {
        if (str == null) {
            return "";
        }
        return exportStringCollection(
                str.getValue().entrySet().stream().map(e -> (sanitizeCommas ? CsvUtils.sanitizeString(e.getValue()) :
                                                             e.getValue()) + "(" + e.getKey() + ")")
                   .collect(Collectors.toSet()));
    }

    static String exportStringCollection(Collection<String> col) {
        assert col != null;
        return String.join(";", col);
    }
}

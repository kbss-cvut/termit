package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.util.CsvUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exports a single Term to a CSV string (single line).
 */
public class CsvTermExporter {

    public String export(Term t) {
        final StringBuilder sb = new StringBuilder(CsvUtils.sanitizeString(t.getUri().toString()));
        sb.append(',').append(TabularTermExportUtils.exportMultilingualString(t.getLabel(), Function.identity(), true));
        exportCollection(sb, t.getAltLabels(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        exportCollection(sb, t.getHiddenLabels(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        sb.append(',')
          .append(TabularTermExportUtils.exportMultilingualString(t.getDefinition(), Utils::markdownToPlainText, true));
        sb.append(',')
          .append(TabularTermExportUtils.exportMultilingualString(t.getDescription(), Utils::markdownToPlainText,
                                                                  true));
        exportCollection(sb, t.getTypes(), String::toString);
        exportCollection(sb, t.getSources(), String::toString);
        exportCollection(sb, t.getParentTerms(), pt -> pt.getUri().toString());
        exportCollection(sb, t.getSubTerms(), TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, t.getRelated(), t.getInverseRelated(), TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, t.getRelatedMatch(), t.getInverseRelatedMatch(),
                                  TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, t.getExactMatchTerms(), t.getInverseExactMatchTerms(),
                                  TabularTermExportUtils::termInfoStringIri);
        sb.append(',')
          .append(TabularTermExportUtils.draftToStatus(t));
        exportCollection(sb, t.getNotations(), String::toString);
        exportCollection(sb, t.getExamples(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        final Map<String, Set<String>> propsToExport =
                t.getProperties() != null ? t.getProperties() : Collections.emptyMap();
        exportCollection(sb, Utils.emptyIfNull(propsToExport.get(DC.Terms.REFERENCES)), String::toString);
        return sb.toString();
    }

    private static <T> void consolidateAndExportMulti(final StringBuilder sb, final Collection<T> collectionOne,
                                                      final Collection<T> collectionTwo, Function<T, String> toString) {
        final Collection<T> toExport = Utils.joinCollections(collectionOne, collectionTwo);
        exportCollection(sb, toExport, toString);
    }

    private static <T> void exportCollection(final StringBuilder sb, final Collection<T> collection,
                                             Function<T, String> toString) {
        sb.append(',');
        if (!CollectionUtils.isEmpty(collection)) {
            sb.append(String.join(TabularTermExportUtils.STRING_DELIMITER,
                                  collection.stream().map(toString).collect(Collectors.toSet())));
        }
    }
}

package cz.cvut.kbss.termit.dto.export;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.Term;
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
 * Term representation that can export itself to CSV.
 */
public class CsvExportTermDto extends Term {

    public String export() {
        final StringBuilder sb = new StringBuilder(CsvUtils.sanitizeString(getUri().toString()));
        sb.append(',').append(TabularTermExportUtils.exportMultilingualString(getLabel(), Function.identity(), true));
        exportCollection(sb, getAltLabels(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        exportCollection(sb, getHiddenLabels(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        sb.append(',')
          .append(TabularTermExportUtils.exportMultilingualString(getDefinition(), Utils::markdownToPlainText, true));
        sb.append(',')
          .append(TabularTermExportUtils.exportMultilingualString(getDescription(), Utils::markdownToPlainText, true));
        exportCollection(sb, getTypes(), String::toString);
        exportCollection(sb, getSources(), String::toString);
        exportCollection(sb, getParentTerms(), pt -> pt.getUri().toString());
        exportCollection(sb, getSubTerms(), TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, getRelated(), getInverseRelated(), TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, getRelatedMatch(), getInverseRelatedMatch(),
                                  TabularTermExportUtils::termInfoStringIri);
        consolidateAndExportMulti(sb, getExactMatchTerms(), getInverseExactMatchTerms(),
                                  TabularTermExportUtils::termInfoStringIri);
        sb.append(',')
          .append(TabularTermExportUtils.draftToStatus(this));
        exportCollection(sb, getNotations(), String::toString);
        exportCollection(sb, getExamples(),
                         str -> TabularTermExportUtils.exportMultilingualString(str, Function.identity(), true));
        final Map<String, Set<String>> propsToExport =
                getProperties() != null ? getProperties() : Collections.emptyMap();
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
            sb.append(TabularTermExportUtils.exportStringCollection(
                    collection.stream().map(toString).collect(Collectors.toSet())));
        }
    }
}

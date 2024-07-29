package cz.cvut.kbss.termit.service.importer.excel;

import com.neovisionaries.i18n.LanguageCode;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.Term;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps an Excel sheet with terms in one language to TermIt {@link Term}s, possibly reusing already processed terms.
 * <p>
 * Note that this class keeps a state.
 */
class LocalizedSheetImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizedSheetImporter.class);

    private static final String FALLBACK_LANGUAGE = "en";

    private final List<Term> existingTerms;

    private Map<String, Integer> attributeToColumn;
    private String langTag;

    private Map<String, Term> labelToTerm;
    private List<ExcelImporter.TermRelationship> rawDataToInsert;

    LocalizedSheetImporter(List<Term> existingTerms) {
        this.existingTerms = existingTerms;
    }

    /**
     * Resolves terms from the specified sheet and returns them.
     * <p>
     * If existing terms are provided to this instance, they will be reused.
     * <p>
     * Note that relationships between terms (except hierarchical ones) are resolved separately and are available via
     * {@link #getRawDataToInsert()}, whose result should be inserted directly into the repository.
     *
     * @param sheet Sheet to process
     * @return Terms resolved from the sheet
     */
    List<Term> resolveTermsFromSheet(Sheet sheet) {
        LOG.debug("Importing terms from sheet '{}'.", sheet.getSheetName());
        this.rawDataToInsert = new ArrayList<>();
        final Optional<LanguageCode> lang = resolveLanguage(sheet);
        if (lang.isEmpty()) {
            return existingTerms;
        }
        this.langTag = lang.get().name();
        LOG.trace("Sheet '{}' mapped to language tag '{}'.", sheet.getSheetName(), langTag);
        final Properties attributeMapping = new Properties();
        this.labelToTerm = new LinkedHashMap<>();
        try {
            attributeMapping.load(resolveColumnMappingFile());
            final Row attributes = sheet.getRow(0);
            this.attributeToColumn = resolveAttributeColumns(attributes, attributeMapping);
        } catch (IOException e) {
            LOG.error("Unable to find attribute mapping for sheet {}. Skipping the sheet.", sheet.getSheetName(), e);
            return Collections.emptyList();
        }
        findTerms(sheet);
        int i = 1;
        for (Map.Entry<String, Term> entry : labelToTerm.entrySet()) {
            mapRowToTermAttributes(entry.getValue(), sheet.getRow(i++));
        }
        return new ArrayList<>(labelToTerm.values());
    }

    private InputStream resolveColumnMappingFile() {
        if (getClass().getClassLoader().getResource("attributes/" + langTag + ".properties") != null) {
            LOG.trace("Loading attribute mapping for language tag '{}'.", langTag);
            return getClass().getClassLoader().getResourceAsStream("attributes/" + langTag + ".properties");
        } else {
            LOG.trace("No attribute mapping found for language tag '{}', falling back to '{}'.", langTag, FALLBACK_LANGUAGE);
            return getClass().getClassLoader().getResourceAsStream("attributes/" + FALLBACK_LANGUAGE + ".properties");
        }
    }

    /**
     * First map terms to labels.
     * <p>
     * This ensures when we are dealing with references between terms, we know we already have all the terms mapped by
     * labels.
     *
     * @param sheet Sheet with terms
     */
    private void findTerms(Sheet sheet) {
        int i;
        for (i = 1; i < sheet.getLastRowNum(); i++) {
            final Row termRow = sheet.getRow(i);
            Term term = existingTerms.size() >= i ? existingTerms.get(i - 1) : new Term();
            final Optional<String> label = getAttributeValue(termRow, SKOS.PREF_LABEL);
            if (label.isEmpty()) {
                LOG.trace("Reached empty label column cell at row {}. Working with {} terms.", i, (i - 1));
                break;
            }
            initSingularMultilingualString(term::getLabel, term::setLabel).set(langTag, label.get());
            labelToTerm.put(label.get(), term);
        }
        for (; i <= existingTerms.size(); i++) {
            labelToTerm.put(existingTerms.get(i - 1).getLabel().get(), existingTerms.get(i - 1));
        }
    }

    private void mapRowToTermAttributes(Term term, Row termRow) {
        getAttributeValue(termRow, SKOS.DEFINITION).ifPresent(
                d -> initSingularMultilingualString(term::getDefinition, term::setDefinition).set(langTag, d));
        getAttributeValue(termRow, SKOS.SCOPE_NOTE).ifPresent(
                sn -> initSingularMultilingualString(term::getDescription, term::setDescription).set(langTag, sn));
        getAttributeValue(termRow, SKOS.ALT_LABEL).ifPresent(
                al -> populatePluralMultilingualString(term::getAltLabels, term::setAltLabels,
                                                       splitIntoMultipleValues(al)));
        getAttributeValue(termRow, SKOS.HIDDEN_LABEL).ifPresent(
                hl -> populatePluralMultilingualString(term::getHiddenLabels, term::setHiddenLabels,
                                                       splitIntoMultipleValues(hl)));
        getAttributeValue(termRow, SKOS.EXAMPLE).ifPresent(
                ex -> populatePluralMultilingualString(term::getExamples, term::setExamples,
                                                       splitIntoMultipleValues(ex)));
        getAttributeValue(termRow, DC.Terms.SOURCE).ifPresent(src -> term.setSources(splitIntoMultipleValues(src)));
        getAttributeValue(termRow, SKOS.BROADER).ifPresent(br -> setParentTerms(term, splitIntoMultipleValues(br)));
        getAttributeValue(termRow, SKOS.NOTATION).ifPresent(nt -> term.setNotations(splitIntoMultipleValues(nt)));
        getAttributeValue(termRow, DC.Terms.REFERENCES).ifPresent(
                nt -> term.setProperties(Map.of(DC.Terms.REFERENCES, splitIntoMultipleValues(nt))));
        getAttributeValue(termRow, SKOS.RELATED).ifPresent(
                rt -> mapSkosRelationship(term, splitIntoMultipleValues(rt), SKOS.RELATED));
    }

    private MultilingualString initSingularMultilingualString(Supplier<MultilingualString> getter,
                                                              Consumer<MultilingualString> setter) {
        if (getter.get() == null) {
            setter.accept(new MultilingualString());
        }
        return getter.get();
    }

    private void populatePluralMultilingualString(
            Supplier<Set<MultilingualString>> getter, Consumer<Set<MultilingualString>> setter, Set<String> values) {
        Set<MultilingualString> attValue = getter.get();
        if (attValue == null) {
            setter.accept(new HashSet<>());
            attValue = getter.get();
        }
        for (String s : values) {
            final Optional<MultilingualString> mls = attValue.stream().filter(m -> !m.contains(langTag)).findFirst();
            if (mls.isPresent()) {
                mls.get().set(langTag, s);
            } else {
                final MultilingualString newMls = MultilingualString.create(s, langTag);
                attValue.add(newMls);
            }
        }
    }

    private void setParentTerms(Term term, Set<String> parentLabels) {
        parentLabels.forEach(label -> {
            final Term parent = labelToTerm.get(label);
            if (parent == null) {
                LOG.warn("No parent term with label '{}' for term '{}'.", label, term.getLabel().get(langTag));
            } else {
                term.addParentTerm(parent);
            }
        });
    }

    private void mapSkosRelationship(Term subject, Set<String> objects, String property) {
        final URI propertyUri = URI.create(property);
        objects.forEach(object -> {
            final Term objectTerm = labelToTerm.get(object);
            if (objectTerm == null) {
                LOG.warn("No term with label '{}' found for term '{}' and relationship <{}>.", object,
                         subject.getLabel().get(langTag), property);
            } else {
                // Term IDs are not generated, yet
                rawDataToInsert.add(new ExcelImporter.TermRelationship(subject, propertyUri, objectTerm));
            }
        });
    }

    List<ExcelImporter.TermRelationship> getRawDataToInsert() {
        return rawDataToInsert;
    }

    private static Optional<LanguageCode> resolveLanguage(Sheet sheet) {
        final List<LanguageCode> codes = LanguageCode.findByName(sheet.getSheetName());
        if (codes.isEmpty()) {
            LOG.debug("No matching language found for sheet '{}'. Skipping it.", sheet.getSheetName());
            return Optional.empty();
        }
        return Optional.of(codes.get(0));
    }

    private static Map<String, Integer> resolveAttributeColumns(Row attributes, Properties attributeMapping) {
        final Map<String, Integer> attributesToColumn = new HashMap<>();
        final Iterator<Cell> it = attributes.cellIterator();
        while (it.hasNext()) {
            final Cell cell = it.next();
            final String columnLabel = cell.getStringCellValue();
            for (Map.Entry<Object, Object> e : attributeMapping.entrySet()) {
                if (e.getValue().equals(columnLabel)) {
                    attributesToColumn.put(e.getKey().toString(), cell.getColumnIndex());
                    break;
                }
            }
        }
        return attributesToColumn;
    }

    private Optional<String> getAttributeValue(Row row, String attributeIri) {
        final Cell cell = row.getCell(attributeToColumn.get(attributeIri));
        if (cell == null) {
            // The cell may be null instead of blank if there are no other columns behind at
            return Optional.empty();
        }
        final String cellValue = row.getCell(attributeToColumn.get(attributeIri)).getStringCellValue();
        return cellValue.isBlank() ? Optional.empty() : Optional.of(cellValue.trim());
    }

    private static Set<String> splitIntoMultipleValues(String value) {
        return Stream.of(value.split(",")).map(String::trim).collect(Collectors.toSet());
    }
}

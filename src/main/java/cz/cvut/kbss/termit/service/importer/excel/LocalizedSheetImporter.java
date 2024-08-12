package cz.cvut.kbss.termit.service.importer.excel;

import com.neovisionaries.i18n.LanguageCode;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
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
 * Note that this class keeps a state and should thus not be reused to process multiple sheets in a single spreadsheet.
 * Instead, a new instance should be created for each sheet.
 */
class LocalizedSheetImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizedSheetImporter.class);

    private static final String FALLBACK_LANGUAGE = "en";

    private final TermRepositoryService termRepositoryService;
    private final PrefixMap prefixMap;
    private final List<Term> existingTerms;
    private final IdentifierResolver idResolver;
    // Namespace expected based on the vocabulary into which the terms will be imported
    private final String expectedTermNamespace;

    private Map<String, Integer> attributeToColumn;
    private String langTag;

    private final Map<String, Term> labelToTerm = new LinkedHashMap<>();
    private final Map<URI, Term> idToTerm = new HashMap<>();
    private List<ExcelImporter.TermRelationship> rawDataToInsert;

    LocalizedSheetImporter(TermRepositoryService termRepositoryService, PrefixMap prefixMap, List<Term> existingTerms,
                           IdentifierResolver idResolver,
                           String expectedTermNamespace) {
        this.termRepositoryService = termRepositoryService;
        this.prefixMap = prefixMap;
        this.existingTerms = existingTerms;
        this.idResolver = idResolver;
        this.expectedTermNamespace = expectedTermNamespace;
        existingTerms.stream().filter(t -> t.getUri() != null).forEach(t -> idToTerm.put(t.getUri(), t));
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
            LOG.trace("No attribute mapping found for language tag '{}', falling back to '{}'.", langTag,
                      FALLBACK_LANGUAGE);
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
        for (i = 1; i <= sheet.getLastRowNum(); i++) {
            final Row termRow = sheet.getRow(i);
            Term term = existingTerms.size() >= i ? existingTerms.get(i - 1) : new Term();
            getAttributeValue(termRow, JsonLd.ID).ifPresent(id -> {
                term.setUri(resolveTermUri(id));
                idToTerm.put(term.getUri(), term);
            });
            final Optional<String> label = getAttributeValue(termRow, SKOS.PREF_LABEL);
            if (label.isPresent()) {
                initSingularMultilingualString(term::getLabel, term::setLabel).set(langTag, label.get());
                labelToTerm.put(label.get(), term);
            } else {
                if (i > existingTerms.size()) {
                    LOG.trace("Reached empty label column cell at row {}.", i);
                    break;
                } else {
                    labelToTerm.put(existingTerms.get(i - 1).getLabel().get(), existingTerms.get(i - 1));
                }
            }
        }
        LOG.trace("Found {} term rows.", i - 1);
    }

    /**
     * If an identifier column is found in the sheet, attempt to use it for term ids.
     * <p>
     * This methods first resolves possible prefixes and then ensures the identifier matches the expected namespace
     * provided by the vocabulary into which we are importing. If it does not match, a new identifier is generated based
     * on the expected namespace and the local name extracted from the identifier present in the sheet.
     *
     * @param id Identifier extracted from the sheet
     * @return Valid term identifier matching target vocabulary
     */
    private URI resolveTermUri(String id) {
        // Handle prefix if it is used
        id = prefixMap.resolvePrefixed(id);
        final URI uriId = URI.create(id);
        if (expectedTermNamespace.equals(IdentifierResolver.extractIdentifierNamespace(uriId))) {
            return URI.create(id);
        } else {
            LOG.trace(
                    "Existing term identifier {} does not correspond to the expected vocabulary term namespace {}. Adjusting the term id.",
                    Utils.uriToString(uriId), expectedTermNamespace);
            final String localName = IdentifierResolver.extractIdentifierFragment(uriId);
            return idResolver.generateIdentifier(expectedTermNamespace, localName);
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
                nt -> term.setProperties(Collections.singletonMap(DC.Terms.REFERENCES, splitIntoMultipleValues(nt))));
        getAttributeValue(termRow, SKOS.RELATED).ifPresent(
                rt -> mapSkosRelated(term, splitIntoMultipleValues(rt)));
        getAttributeValue(termRow, SKOS.RELATED_MATCH).ifPresent(
                rtm -> mapSkosMatchProperties(term, SKOS.RELATED_MATCH, splitIntoMultipleValues(rtm)));
        getAttributeValue(termRow, SKOS.EXACT_MATCH).ifPresent(
                exm -> mapSkosMatchProperties(term, SKOS.EXACT_MATCH, splitIntoMultipleValues(exm)));
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

    private void setParentTerms(Term term, Set<String> parents) {
        parents.forEach(parentIdentification -> {
            final Term parent = getTerm(parentIdentification);
            if (parent == null) {
                LOG.warn("No parent term with label or identifier '{}' found for term '{}'.", parentIdentification,
                         term.getLabel().get(langTag));
            } else {
                term.addParentTerm(parent);
            }
        });
    }

    private void mapSkosRelated(Term subject, Set<String> objects) {
        final URI propertyUri = URI.create(SKOS.RELATED);
        objects.forEach(object -> {
            try {
                final Term objectTerm = getTerm(object);
                if (objectTerm == null) {
                    LOG.warn("No term with label '{}' found for term '{}' and relationship <{}>.", object,
                             subject.getLabel().get(langTag), SKOS.RELATED);
                } else {
                    // Term IDs may not be generated, yet
                    rawDataToInsert.add(new ExcelImporter.TermRelationship(subject, propertyUri, objectTerm));
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Could not create URI for value '{}' and it does not reference another term by label either",
                         object);
            }
        });
    }

    private Term getTerm(String identification) {
        return labelToTerm.containsKey(identification) ? labelToTerm.get(identification) :
               idToTerm.get(URI.create(prefixMap.resolvePrefixed(identification)));
    }

    private void mapSkosMatchProperties(Term subject, String property, Set<String> objects) {
        final URI propertyUri = URI.create(property);
        objects.stream().map(id -> URI.create(prefixMap.resolvePrefixed(id))).filter(termRepositoryService::exists)
               .forEach(uri -> rawDataToInsert.add(
                       new ExcelImporter.TermRelationship(subject, propertyUri, new Term(uri))));
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
        if (!attributeToColumn.containsKey(attributeIri)) {
            // Attribute column is not present at all
            return Optional.empty();
        }
        final Cell cell = row.getCell(attributeToColumn.get(attributeIri));
        if (cell == null) {
            // The cell may be null instead of blank if there are no other columns behind at
            return Optional.empty();
        }
        final String cellValue = row.getCell(attributeToColumn.get(attributeIri)).getStringCellValue();
        return cellValue.isBlank() ? Optional.empty() : Optional.of(cellValue.trim());
    }

    private static Set<String> splitIntoMultipleValues(String value) {
        return Stream.of(value.split(TabularTermExportUtils.STRING_DELIMITER)).map(String::trim)
                     .collect(Collectors.toSet());
    }
}

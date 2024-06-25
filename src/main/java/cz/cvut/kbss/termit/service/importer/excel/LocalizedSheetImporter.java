package cz.cvut.kbss.termit.service.importer.excel;

import com.neovisionaries.i18n.LanguageCode;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LocalizedSheetImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LocalizedSheetImporter.class);

    private Map<String, Integer> attributeToColumn;

    List<Term> resolveTermsFromSheet(Sheet sheet) {
        LOG.debug("Importing terms from sheet '{}'.", sheet.getSheetName());
        final LanguageCode lang = resolveLanguage(sheet);
        final String langTag = lang.name();
        LOG.trace("Sheet '{}' mapped to language tage '{}'.", sheet.getSheetName(), langTag);
        final Properties attributeMapping = new Properties();
        final Map<String, Term> labelToTerm = new LinkedHashMap<>();
        try {
            attributeMapping.load(
                    getClass().getClassLoader().getResourceAsStream("attributes/" + langTag + ".properties"));
            final Row attributes = sheet.getRow(0);
            this.attributeToColumn = resolveAttributeColumns(attributes, attributeMapping);
        } catch (IOException e) {
            LOG.error("Unable to find attribute mapping for sheet {}. Skipping the sheet.", sheet.getSheetName(), e);
            return Collections.emptyList();
        }
        for (int i = 1; i < sheet.getLastRowNum(); i++) {
            final Row termRow = sheet.getRow(i);
            final Term term = new Term();
            final Optional<String> label = getAttributeValue(termRow, SKOS.PREF_LABEL);
            if (label.isEmpty()) {
                LOG.trace("Reached empty label column cell at row {}. Finished processing sheet.", i);
                break;
            }
            term.setLabel(MultilingualString.create(label.get(), langTag));
            getAttributeValue(termRow, SKOS.DEFINITION).ifPresent(
                    d -> term.setDefinition(MultilingualString.create(d, langTag)));
            getAttributeValue(termRow, SKOS.SCOPE_NOTE).ifPresent(
                    sn -> term.setDescription(MultilingualString.create(sn, langTag)));
            getAttributeValue(termRow, SKOS.ALT_LABEL).ifPresent(al -> term.setAltLabels(
                    splitIntoMultipleValues(al).stream().map(s -> MultilingualString.create(s, langTag)).collect(
                            Collectors.toSet())));
            getAttributeValue(termRow, SKOS.HIDDEN_LABEL).ifPresent(hl -> term.setHiddenLabels(
                    splitIntoMultipleValues(hl).stream().map(s -> MultilingualString.create(s, langTag)).collect(
                            Collectors.toSet())));
            getAttributeValue(termRow, SKOS.EXAMPLE).ifPresent(ex -> term.setExamples(
                    splitIntoMultipleValues(ex).stream().map(s -> MultilingualString.create(s, langTag)).collect(
                            Collectors.toSet())));
            getAttributeValue(termRow, DC.Terms.SOURCE).ifPresent(src -> term.setSources(splitIntoMultipleValues(src)));
            getAttributeValue(termRow, SKOS.NOTATION).ifPresent(nt -> term.setNotations(splitIntoMultipleValues(nt)));
            getAttributeValue(termRow, DC.Terms.REFERENCES).ifPresent(
                    nt -> term.setProperties(Map.of(DC.Terms.REFERENCES, splitIntoMultipleValues(nt))));
            labelToTerm.put(label.get(), term);
        }
        return new ArrayList<>(labelToTerm.values());
    }

    private static LanguageCode resolveLanguage(Sheet sheet) {
        final List<LanguageCode> codes = LanguageCode.findByName(sheet.getSheetName());
        if (codes.isEmpty()) {
            throw new VocabularyImportException("Unsupported sheet language " + sheet.getSheetName());
        }
        return codes.get(0);
    }

    private Map<String, Integer> resolveAttributeColumns(Row attributes, Properties attributeMapping) {
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
        final String cellValue = row.getCell(attributeToColumn.get(attributeIri)).getStringCellValue();
        return cellValue.isBlank() ? Optional.empty() : Optional.of(cellValue.trim());
    }

    private Set<String> splitIntoMultipleValues(String value) {
        return Stream.of(value.split(",")).map(String::trim).collect(Collectors.toSet());
    }
}

package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.CsvUtils;
import cz.cvut.kbss.termit.util.Vocabulary;
import cz.cvut.kbss.termit.validation.PrimaryNotBlank;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configurable
@Audited
@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "description", "subTerms"})
public class Term extends AbstractTerm implements HasTypes {

    /**
     * Names of columns used in term export.
     */
    public static final List<String> EXPORT_COLUMNS = Collections
            .unmodifiableList(
                    Arrays.asList("IRI", "Label", "Alternative Labels", "Hidden Labels", "Definition", "Description",
                            "Types", "Sources", "Parent term",
                            "SubTerms", "Draft"));

    @Autowired
    @Transient
    private Configuration config;

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.HIDDEN_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<Term> parentTerms;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj_definice_termu, fetch = FetchType.EAGER)
    private TermDefinitionSource definitionSource;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    @Types
    private Set<String> types;


    /**
     * Sets label in the application-configured language.
     * <p>
     * This is a convenience method allowing to skip working with {@link MultilingualString} instances.
     *
     * @param label Label value to set
     * @see #setLabel(MultilingualString)
     */
    @JsonIgnore
    public void setPrimaryLabel(String label) {
        if (this.getLabel() == null) {
            this.setLabel(MultilingualString.create(label, config.get(ConfigParam.LANGUAGE)));
        } else {
            this.getLabel().set(config.get(ConfigParam.LANGUAGE), label);
        }
    }

    /**
     * Gets label in the application-configured language.
     * <p>
     * This is a convenience method allowing to skip working with {@link MultilingualString} instances.
     *
     * @return Label value
     * @see #getLabel()
     */
    @JsonIgnore
    public String getPrimaryLabel() {
        return getLabel() != null ? getLabel().get(config.get(ConfigParam.LANGUAGE)) : null;
    }

    public Set<MultilingualString> getAltLabels() {
        return altLabels;
    }

    public void setAltLabels(Set<MultilingualString> altLabels) {
        this.altLabels = altLabels;
    }

    public Set<MultilingualString> getHiddenLabels() {
        return hiddenLabels;
    }

    public void setHiddenLabels(Set<MultilingualString> hiddenLabels) {
        this.hiddenLabels = hiddenLabels;
    }

    public MultilingualString getDescription() {
        return description;
    }

    public void setDescription(MultilingualString description) {
        this.description = description;
    }

    public Set<Term> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<Term> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public void addParentTerm(Term term) {
        if (parentTerms == null) {
            this.parentTerms = new HashSet<>();
        }
        parentTerms.add(term);
    }

    public Set<String> getSources() {
        return sources;
    }

    public void setSources(Set<String> source) {
        this.sources = source;
    }

    public TermDefinitionSource getDefinitionSource() {
        return definitionSource;
    }

    public void setDefinitionSource(TermDefinitionSource definitionSource) {
        this.definitionSource = definitionSource;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<String>> properties) {
        this.properties = properties;
    }

    private static <T> void exportMulti(final StringBuilder sb, final Set<T> collection, Function<T, String> toString) {
        sb.append(',');
        if (collection != null && !collection.isEmpty()) {
            sb.append(exportCollection(collection.stream().map(toString).collect(Collectors.toSet())));
        }
    }

    /**
     * Generates a CSV line representing this term.
     * <p>
     * The line contains columns specified in {@link #EXPORT_COLUMNS}
     *
     * @return CSV representation of this term
     */
    public String toCsv() {
        final StringBuilder sb = new StringBuilder(CsvUtils.sanitizeString(getUri().toString()));
        sb.append(',').append(exportMultilingualString(getLabel()));
        exportMulti(sb, altLabels, Term::exportMultilingualString);
        exportMulti(sb, hiddenLabels, Term::exportMultilingualString);
        sb.append(',').append(exportMultilingualString(getDefinition()));
        sb.append(',').append(exportMultilingualString(description));
        exportMulti(sb, types, String::toString);
        exportMulti(sb, sources, String::toString);
        exportMulti(sb, parentTerms, pt -> pt.getUri().toString());
        exportMulti(sb, getSubTerms(), pt -> pt.getUri().toString());
        sb.append(',');
        sb.append(isDraft());
        return sb.toString();
    }

    private static String exportMultilingualString(MultilingualString str) {
        if (str == null) {
            return "";
        }
        return exportCollection(str.getValue().values());
    }

    private static String exportCollection(Collection<String> col) {
        return CsvUtils.sanitizeString(String.join(";", col));
    }

    /**
     * Generates an Excel line (line with tab separated values) representing this term.
     * <p>
     * The line contains columns specified in {@link #EXPORT_COLUMNS}
     *
     * @param row The row into which data of this term will be generated
     */
    public void toExcel(Row row) {
        Objects.requireNonNull(row);
        row.createCell(0).setCellValue(getUri().toString());
        row.createCell(1).setCellValue(getLabel().toString());
        if (altLabels != null) {
            row.createCell(2).setCellValue(String.join(";",
                    altLabels.stream().map(Term::exportMultilingualString).collect(Collectors.toSet())));
        }
        if (hiddenLabels != null) {
            row.createCell(3).setCellValue(String.join(";",
                    hiddenLabels.stream().map(Term::exportMultilingualString).collect(Collectors.toSet())));
        }
        if (getDefinition() != null) {
            row.createCell(4).setCellValue(getDefinition().toString());
        }
        if (description != null) {
            row.createCell(5).setCellValue(description.toString());
        }
        if (types != null) {
            row.createCell(6).setCellValue(String.join(";", types));
        }
        if (sources != null) {
            row.createCell(7).setCellValue(String.join(";", sources));
        }
        if (parentTerms != null) {
            row.createCell(8)
               .setCellValue(String.join(";",
                       parentTerms.stream().map(pt -> pt.getUri().toString()).collect(Collectors.toSet())));
        }
        if (getSubTerms() != null) {
            row.createCell(9)
               .setCellValue(String.join(";",
                       getSubTerms().stream().map(ti -> ti.getUri().toString()).collect(Collectors.toSet())));
        }
        row.createCell(10).setCellValue(isDraft());
    }

    /**
     * Checks whether this term has a parent term in the same vocabulary.
     *
     * @return Whether this term has parent in its vocabulary. Returns {@code false} also if this term has no parent
     * term at all
     */
    public boolean hasParentInSameVocabulary() {
        return parentTerms != null && parentTerms.stream().anyMatch(p -> p.getGlossary().equals(getGlossary()));
    }

    @Override
    public String toString() {
        return "Term{" +
                getLabel() +
                " <" + getUri() + '>' +
                ", types=" + types +
                '}';
    }

    public static Field getParentTermsField() {
        try {
            return Term.class.getDeclaredField("parentTerms");
        } catch (NoSuchFieldException e) {
            throw new TermItException("Fatal error! Unable to retrieve \"parentTerms\" field.", e);
        }
    }

}

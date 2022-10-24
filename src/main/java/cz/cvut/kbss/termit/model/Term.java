package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.annotation.JsonLdAttributeOrder;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.CsvUtils;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.poi.ss.usermodel.Row;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configurable
@Audited
@OWLClass(iri = SKOS.CONCEPT)
@JsonLdAttributeOrder({"uri", "label", "description", "subTerms"})
public class Term extends AbstractTerm implements HasTypes, SupportsSnapshots {

    /**
     * Names of columns used in term export.
     * <p>
     */
    public static final List<String> EXPORT_COLUMNS = List.of("IRI", "Label", "Alternative Labels", "Hidden Labels",
            "Definition", "Description", "Types", "Sources",
            "Parent Terms", "SubTerms", "Related Terms",
            "Related Match Terms", "Exact Match Terms", "Draft",
            "Notation", "Example", "References"
    );

    @Autowired
    @Transient
    private transient Configuration config;

    @OWLAnnotationProperty(iri = SKOS.ALT_LABEL)
    private Set<MultilingualString> altLabels;

    @OWLAnnotationProperty(iri = SKOS.HIDDEN_LABEL)
    private Set<MultilingualString> hiddenLabels;

    @OWLAnnotationProperty(iri = SKOS.SCOPE_NOTE)
    private MultilingualString description;

    @OWLAnnotationProperty(iri = DC.Terms.SOURCE, simpleLiteral = true)
    private Set<String> sources;

    @OWLObjectProperty(iri = SKOS.EXACT_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> exactMatchTerms;

    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseExactMatchTerms;

    /**
     * Parent terms from the same vocabulary.
     */
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<Term> parentTerms;

    /**
     * Parent terms from different vocabularies.
     * <p>
     * Represents the {@code skos:broadMatch} property.
     */
    @JsonIgnore
    @OWLObjectProperty(iri = SKOS.BROAD_MATCH, fetch = FetchType.EAGER)
    private Set<Term> externalParentTerms;

    @OWLObjectProperty(iri = SKOS.RELATED, fetch = FetchType.EAGER)
    private Set<TermInfo> related;

    // Terms related by the virtue of related being symmetric, i.e. those that assert relation with this term
    // Loaded outside of JOPA entity loading mechanism
    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseRelated;

    // relatedMatch are related terms from a different vocabulary
    @OWLObjectProperty(iri = SKOS.RELATED_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> relatedMatch;

    // Terms from a different vocabulary related by the virtue of relatedMatch being symmetric, i.e. those that assert relation with this term
    // Loaded outside of JOPA entity loading mechanism
    @Transient
    @JsonIgnore
    private Set<TermInfo> inverseRelatedMatch;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zdroj_definice_termu, fetch = FetchType.EAGER)
    private TermDefinitionSource definitionSource;

    @Properties(fetchType = FetchType.EAGER)
    private Map<String, Set<String>> properties;

    public Term() {
    }

    public Term(URI uri) {
        setUri(uri);
    }

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
            this.setLabel(MultilingualString.create(label, config.getPersistence().getLanguage()));
        } else {
            this.getLabel().set(config.getPersistence().getLanguage(), label);
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
        return getLabel() != null ? getLabel().get(config.getPersistence().getLanguage()) : null;
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

    public Set<Term> getExternalParentTerms() {
        return externalParentTerms;
    }

    public void setExternalParentTerms(Set<Term> externalParentTerms) {
        this.externalParentTerms = externalParentTerms;
    }

    /**
     * Adds the specified term to the parent terms of this instance.
     * <p>
     * If the specified term is from the same glossary, it is added to {@code parentTerms}, otherwise, it is added to
     * the {@code importedParentTerms}.
     *
     * @param term Term to add as parent
     */
    public void addParentTerm(Term term) {
        Objects.requireNonNull(term);
        if (!Objects.equals(getGlossary(), term.getGlossary())) {
            if (externalParentTerms == null) {
                this.externalParentTerms = new HashSet<>();
            }
            externalParentTerms.add(term);
        } else {
            if (parentTerms == null) {
                this.parentTerms = new HashSet<>();
            }
            parentTerms.add(term);
        }
    }

    public Set<TermInfo> getRelated() {
        return related;
    }

    public void setRelated(Set<TermInfo> related) {
        this.related = related;
    }

    public void addRelatedTerm(TermInfo ti) {
        Objects.requireNonNull(ti);
        if (related == null) {
            this.related = new LinkedHashSet<>();
        }
        related.add(ti);
    }

    public Set<TermInfo> getInverseRelated() {
        return inverseRelated;
    }

    public void setInverseRelated(Set<TermInfo> inverseRelated) {
        this.inverseRelated = inverseRelated;
    }

    public Set<TermInfo> getRelatedMatch() {
        return relatedMatch;
    }

    public void setRelatedMatch(Set<TermInfo> relatedMatch) {
        this.relatedMatch = relatedMatch;
    }

    public void addRelatedMatchTerm(TermInfo ti) {
        Objects.requireNonNull(ti);
        if (relatedMatch == null) {
            this.relatedMatch = new LinkedHashSet<>();
        }
        relatedMatch.add(ti);
    }

    public Set<TermInfo> getInverseRelatedMatch() {
        return inverseRelatedMatch;
    }

    public void setInverseRelatedMatch(Set<TermInfo> inverseRelatedMatch) {
        this.inverseRelatedMatch = inverseRelatedMatch;
    }

    public void setExactMatchTerms(Set<TermInfo> exactMatchTerms) {
        this.exactMatchTerms = exactMatchTerms;
    }

    public Set<TermInfo> getExactMatchTerms() {
        return exactMatchTerms;
    }

    public void addExactMatch(TermInfo term) {
        Objects.requireNonNull(term);
        if (exactMatchTerms == null) {
            this.exactMatchTerms = new HashSet<>();
        }
        exactMatchTerms.add(term);
    }

    public Set<TermInfo> getInverseExactMatchTerms() {
        return inverseExactMatchTerms;
    }

    public void setInverseExactMatchTerms(Set<TermInfo> inverseExactMatchTerms) {
        this.inverseExactMatchTerms = inverseExactMatchTerms;
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

    public Map<String, Set<String>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Set<String>> properties) {
        this.properties = properties;
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
        exportMulti(sb, getTypes(), String::toString);
        exportMulti(sb, sources, String::toString);
        exportMulti(sb, parentTerms, pt -> pt.getUri().toString());
        exportMulti(sb, getSubTerms(), Term::termInfoStringIri);
        consolidateAndExportMulti(sb, related, inverseRelated, Term::termInfoStringIri);
        consolidateAndExportMulti(sb, relatedMatch, inverseRelatedMatch, Term::termInfoStringIri);
        consolidateAndExportMulti(sb, exactMatchTerms, inverseExactMatchTerms, Term::termInfoStringIri);
        sb.append(',');
        sb.append(isDraft());
        final Map<String, Set<String>> propsToExcel = properties != null ? properties : Collections.emptyMap();
        exportMulti(sb, Utils.emptyIfNull(propsToExcel.get(SKOS.NOTATION)), String::toString);
        exportMulti(sb, Utils.emptyIfNull(propsToExcel.get(SKOS.EXAMPLE)), String::toString);
        exportMulti(sb, Utils.emptyIfNull(propsToExcel.get(DCTERMS.REFERENCES)), String::toString);
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

    private static <T> void exportMulti(final StringBuilder sb, final Collection<T> collection,
                                        Function<T, String> toString) {
        sb.append(',');
        if (!CollectionUtils.isEmpty(collection)) {
            sb.append(exportCollection(collection.stream().map(toString).collect(Collectors.toSet())));
        }
    }

    private static <T> void consolidateAndExportMulti(final StringBuilder sb, final Collection<T> collectionOne,
                                                      final Collection<T> collectionTwo, Function<T, String> toString) {
        final Collection<T> toExport = Utils.joinCollections(collectionOne, collectionTwo);
        exportMulti(sb, toExport, toString);
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
        row.createCell(2)
           .setCellValue(exportCollection(Utils.emptyIfNull(altLabels).stream().map(Term::exportMultilingualString)
                                               .collect(Collectors.toSet())));
        row.createCell(3)
           .setCellValue(exportCollection(Utils.emptyIfNull(hiddenLabels).stream().map(Term::exportMultilingualString)
                                               .collect(Collectors.toSet())));
        if (getDefinition() != null) {
            row.createCell(4).setCellValue(getDefinition().toString());
        }
        if (description != null) {
            row.createCell(5).setCellValue(description.toString());
        }
        row.createCell(6).setCellValue(exportCollection(Utils.emptyIfNull(getTypes())));
        row.createCell(7).setCellValue(exportCollection(Utils.emptyIfNull(sources)));
        row.createCell(8)
           .setCellValue(exportCollection(Utils.emptyIfNull(parentTerms).stream().map(pt -> pt.getUri().toString())
                                               .collect(Collectors.toSet())));
        row.createCell(9)
           .setCellValue(exportCollection(Utils.emptyIfNull(getSubTerms()).stream().map(Term::termInfoStringIri)
                                               .collect(Collectors.toSet())));
        row.createCell(10).setCellValue(exportCollection(Utils.joinCollections(related, inverseRelated).stream()
                                                              .map(Term::termInfoStringIri)
                                                              .distinct()
                                                              .collect(Collectors.toList())));
        row.createCell(11)
           .setCellValue(exportCollection(Utils.joinCollections(relatedMatch, inverseRelatedMatch).stream()
                                               .map(Term::termInfoStringIri)
                                               .distinct()
                                               .collect(Collectors.toList())));
        row.createCell(12)
           .setCellValue(exportCollection(Utils.joinCollections(exactMatchTerms, inverseExactMatchTerms).stream()
                                               .map(Term::termInfoStringIri)
                                               .distinct()
                                               .collect(Collectors.toList())));
        row.createCell(13).setCellValue(isDraft());
        if (properties != null) {
            row.createCell(14).setCellValue(Utils.emptyIfNull(properties.get(SKOS.NOTATION)).toString());
            row.createCell(15).setCellValue(Utils.emptyIfNull(properties.get(SKOS.EXAMPLE)).toString());
            row.createCell(16).setCellValue(Utils.emptyIfNull(properties.get(DCTERMS.REFERENCES)).toString());
        }
    }

    private static String termInfoStringIri(TermInfo ti) {
        assert ti != null;
        return ti.getUri().toString();
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

    /**
     * Consolidates the asserted related (relatedMatch, exactMatch) and inferred inverse related (relatedMatch,
     * exactMatch) terms into related (relatedMatch, exactMatch).
     * <p>
     * This basically means copying items from {@code inverseRelated} ({@code inverseRelatedMatch}, {@code exactMatch})
     * to {@code related} ({@code relatedMatch}, {@code exactMatch}) so that they act as they should in reality because
     * of skos:related (skos:relatedMatch, skos:exactMatch) being symmetric.
     */
    public void consolidateInferred() {
        if (inverseRelated != null) {
            inverseRelated.forEach(ti -> addRelatedTerm(new TermInfo(ti)));
        }
        if (inverseRelatedMatch != null) {
            inverseRelatedMatch.forEach(ti -> addRelatedMatchTerm(new TermInfo(ti)));
        }
        if (inverseExactMatchTerms != null) {
            inverseExactMatchTerms.forEach(ti -> addExactMatch(new TermInfo(ti)));
        }
    }

    /**
     * Consolidates parent and external parent terms into just parent terms.
     * <p>
     * This is based on the fact that external parents are a special case of parent terms (SKOS broadMatch is a
     * sub-property of broader). Clients need not know about their distinction, which is important only at repository
     * level.
     *
     * @see #splitExternalAndInternalParents()
     */
    public void consolidateParents() {
        if (externalParentTerms != null && !externalParentTerms.isEmpty()) {
            if (parentTerms == null) {
                parentTerms = new LinkedHashSet<>();
            }
            parentTerms.addAll(externalParentTerms);
        }
    }

    /**
     * Splits consolidated parent terms into external and (internal) parent terms.
     * <p>
     * This split is driven by the fact that external parents belong to a different glossary than this term and should
     * thus be differentiated on repository level.
     * <p>
     * This method does the inverse of {@link #consolidateParents()}.
     *
     * @see #consolidateParents()
     */
    public void splitExternalAndInternalParents() {
        if (parentTerms == null || parentTerms.isEmpty()) {
            return;
        }
        final Set<Term> parents = new LinkedHashSet<>();
        final Set<Term> externalParents = new LinkedHashSet<>();
        for (Term p : parentTerms) {
            if (Objects.equals(getGlossary(), p.getGlossary())) {
                parents.add(p);
            } else {
                externalParents.add(p);
            }
        }
        this.parentTerms = parents;
        this.externalParentTerms = externalParents;
    }

    @Override
    public String toString() {
        return "Term{" +
                getLabel() +
                " <" + getUri() + '>' +
                ", types=" + getTypes() +
                '}';
    }
}

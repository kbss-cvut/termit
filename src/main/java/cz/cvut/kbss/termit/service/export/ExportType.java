package cz.cvut.kbss.termit.service.export;

/**
 * Defines the types of how a vocabulary may be exported.
 */
public enum ExportType {

    /**
     * Export a vocabulary as a SKOS glossary.
     * <p>
     * Only SKOS-based attributes of terms are exported.
     */
    SKOS,
    /**
     * Export a vocabulary with all available data.
     * <p>
     * All term attributes are exported.
     */
    SKOS_FULL,
    /**
     * Export a vocabulary as a SKOS glossary together with terms from other vocabularies referenced by the exported
     * ones.
     * <p>
     * That is, besides the exported glossary, terms from other vocabularies referenced by terms from the exported
     * glossary via any of a specified set of are included in the result as well. Note that only SKOS properties (e.g.,
     * skos:exactMatch, skos:relatedMatch) are supported.
     * <p>
     * Only SKOS-based attributes of terms are exported.
     */
    SKOS_WITH_REFERENCES,
    /**
     * Export a vocabulary as a SKOS glossary together with terms from other vocabularies referenced by the exported
     * ones.
     * <p>
     * That is, besides the exported glossary, terms from other vocabularies referenced by terms from the exported
     * glossary via any of a specified set of are included in the result as well. Note that only SKOS properties (e.g.,
     * skos:exactMatch, skos:relatedMatch) are supported.
     * <p>
     * All term attributes are exported.
     */
    SKOS_FULL_WITH_REFERENCES
}

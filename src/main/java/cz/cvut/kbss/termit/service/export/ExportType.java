/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

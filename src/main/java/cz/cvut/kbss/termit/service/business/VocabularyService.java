/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Interface of business logic concerning vocabularies.
 */
public interface VocabularyService
        extends CrudService<Vocabulary>, ChangeRecordProvider<Vocabulary>, SupportsLastModification {

    /**
     * Gets identifiers of all vocabularies imported by the specified vocabulary, including transitively imported ones.
     *
     * @param entity Base vocabulary, whose imports should be retrieved
     * @return Collection of (transitively) imported vocabularies
     */
    Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity);

    /**
     * Gets identifiers of all vocabularies whose terms are in a SKOS relationship with the specified vocabulary or are
     * explicitly imported by it.
     * <p>
     * This includes transitively related.
     *
     * @param entity Base vocabulary whose related vocabularies to return
     * @return Set of vocabulary identifiers
     */
    Set<URI> getRelatedVocabularies(Vocabulary entity);

    /**
     * Imports a new vocabulary from the specified file.
     * <p>
     * The file could be a text file containing RDF.
     *
     * @param rename true, if the IRIs should be modified in order to prevent clashes with existing data
     * @param file   File from which to import the vocabulary
     * @return The imported vocabulary metadata
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyImportException If the import fails
     */
    Vocabulary importVocabulary(boolean rename, MultipartFile file);

    /**
     * Imports a vocabulary from the specified file.
     * <p>
     * The file could be a text file containing RDF. If a vocabulary with the specified identifier already exists, its
     * content is overridden by the input data.
     *
     * @param vocabularyIri IRI of the vocabulary to be created
     * @param file          File from which to import the vocabulary
     * @return The imported vocabulary metadata
     * @throws cz.cvut.kbss.termit.exception.importing.VocabularyImportException If the import fails
     */
    Vocabulary importVocabulary(URI vocabularyIri, MultipartFile file);

    /**
     * Gets aggregated information about changes in the specified vocabulary.
     *
     * @param vocabulary Vocabulary whose content changes to get
     * @return List of aggregated change objects, ordered by date in ascending order
     */
    List<AggregatedChangeInfo> getChangesOfContent(Vocabulary vocabulary);

    /**
     * Runs text analysis on the definitions of all terms in the specified vocabulary, including terms in the
     * transitively imported vocabularies.
     *
     * @param vocabulary Vocabulary to be analyzed
     */
    void runTextAnalysisOnAllTerms(Vocabulary vocabulary);

    /**
     * Runs text analysis on definitions of all terms in all vocabularies.
     */
    void runTextAnalysisOnAllVocabularies();

    /**
     * Removes a vocabulary if: - it is not a document vocabulary, or - it is imported by another vocabulary, or - it
     * contains terms.
     *
     * @param asset Vocabulary to remove
     */
    void remove(Vocabulary asset);

    /**
     * Validates a vocabulary: - it checks glossary rules, - it checks OntoUml constraints.
     *
     * @param validate Vocabulary to validate
     */
    List<ValidationResult> validateContents(Vocabulary validate);

    /**
     * Gets the number of terms in the specified vocabulary.
     * <p>
     * Note that this methods counts the terms regardless of their hierarchical position.
     *
     * @param vocabulary Vocabulary whose terms should be counted
     * @return Number of terms in the vocabulary, 0 for empty or unknown vocabulary
     */
    Integer getTermCount(Vocabulary vocabulary);

    /**
     * Creates a snapshot of the specified vocabulary.
     * <p>
     * The result is a read-only snapshot of the specified vocabulary, its content and any vocabularies it depends on or
     * that depend on it.
     *
     * @param vocabulary Vocabulary to snapshot
     */
    Snapshot createSnapshot(Vocabulary vocabulary);

    /**
     * Finds snapshots of the specified asset.
     * <p>
     * Note that the list does not contain the currently active version of the asset, as it is not considered a
     * snapshot.
     *
     * @param asset Asset whose snapshots to find
     * @return List of snapshots, sorted by date of creation (latest first)
     */
    List<Snapshot> findSnapshots(Vocabulary asset);

    /**
     * Finds a version of the specified asset valid at the specified instant.
     * <p>
     * The result may be the current version, in case there is no snapshot matching the instant.
     *
     * @param asset Asset whose version to get
     * @param at    Instant at which the asset should be returned
     * @return Version of the asset valid at the specified instant
     */
    Vocabulary findVersionValidAt(Vocabulary asset, Instant at);

    /**
     * Resolves preferred prefix of a vocabulary with the specified identifier.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Prefix declaration, possibly empty
     */
    PrefixDeclaration resolvePrefix(URI vocabularyUri);
}

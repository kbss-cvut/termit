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
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Collection;
import java.util.List;

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
     * Imports vocabulary from the specified file.
     * <p>
     * The file could be a text file containing RDF, or it could be a ZIP file containing separate vocabulary, glossary
     * and model files.
     *
     * @param vocabularyIri IRI of the vocabulary to be created.
     * @param file File from which to import the vocabulary
     * @return The imported vocabulary metadata
     */
    Vocabulary importVocabulary(String vocabularyIri, MultipartFile file);

    /**
     * Gets change records of the listed assets.
     *
     * @param asset Assets to find change records for
     * @return List of change records, ordered by record timestamp in descending order
     */
    List<AbstractChangeRecord> getChangesOfContent(Vocabulary asset);

    /**
     * Removes a vocabulary if:
     * - it is not a document vocabulary, or
     * - it is imported by another vocabulary, or
     * - it contains terms.
     *
     * @param asset Vocabulary to remove
     */
    void remove(Vocabulary asset);

    /**
     * Validates a vocabulary:
     * - it checks glossary rules,
     * - it checks OntoUml constraints.
     *
     * @param validate Vocabulary to validate
     */
    List<ValidationResult> validateContents(Vocabulary validate);
}

/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.Vocabulary;
import java.util.List;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * Business logic concerning external vocabularies.
 * 
 */
public interface ExternalVocabularyService extends ApplicationEventPublisherAware{
    
    /**
     * Fetches a list of vocabularies available for import.
     *
     * @return list of available vocabulary information or empty list when connection
     * failed
     */
    public List<RdfsResource> getAvailableVocabularies();
    
    /**
     * Imports multiple vocabularies from external source.
     *
     * @param vocabularyIris List of iris of vocabularies that shall be
     * imported.
     * @return first imported Vocabulary
     */
    public Vocabulary importFromExternalUris(List<String> vocabularyIris);
    
    /**
     * Reload already imported external vocabularies.
     */
    public void reloadVocabularies();

}

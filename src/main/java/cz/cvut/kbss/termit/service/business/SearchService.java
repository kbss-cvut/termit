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

import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final SearchDao searchDao;

    private final WorkspaceMetadataProvider workspaceMetadataProvider;

    @Autowired
    public SearchService(SearchDao searchDao,
                         WorkspaceMetadataProvider workspaceMetadataProvider) {
        this.searchDao = searchDao;
        this.workspaceMetadataProvider = workspaceMetadataProvider;
    }

    /**
     * Executes full text search in assets.
     *
     * @param searchString String to search by
     * @return Matching assets
     */
    public List<FullTextSearchResult> fullTextSearch(String searchString) {
        return searchDao.fullTextSearch(searchString,
                workspaceMetadataProvider.getCurrentWorkspaceMetadata().getVocabularyContexts());
    }
}

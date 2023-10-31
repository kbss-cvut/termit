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
package cz.cvut.kbss.termit.service.workspace;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * Manages workspace.
 * <p>
 * In some deployments, the user is able to edit only a specific set of vocabularies (more precisely, working copies of
 * the vocabularies). This service manages this process.
 */
@Service
public class WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceService.class);

    private final VocabularyContextMapper contextMapper;

    private final EditableVocabularies vocabularies;

    public WorkspaceService(VocabularyContextMapper contextMapper, EditableVocabularies vocabularies) {
        this.contextMapper = contextMapper;
        this.vocabularies = vocabularies;
    }

    /**
     * Opens the specified set of repository contexts for editing.
     * <p>
     * This assumes that the contexts contain vocabularies that are to be editable. All other vocabularies remain
     * read-only and attempting to edit them will result in an exception.
     * <p>
     * The specified contexts also override vocabulary contexts provided by default by {@link
     * cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper}, as they would normally point to the canonical
     * (non-editable) versions of the vocabularies, whereas the contexts provided to this method will contain editable
     * copies of the vocabularies.
     * <p>
     * Note that it is expected that the contexts already exist, it is not the responsibility of TermIt to create them
     * or populate them with any seed data.
     * <p>
     * If contexts were previously open for editing during a session, they are cleared and only the specified are open.
     *
     * @param contexts Contexts to open for editing
     */
    public void openForEditing(Collection<URI> contexts) {
        Objects.requireNonNull(contexts);
        LOG.debug("Opening the following vocabulary contexts for editing: {}", contexts);
        vocabularies.clear();
        contexts.forEach(ctx -> vocabularies.registerEditableVocabulary(contextMapper.getVocabularyInContext(ctx)
                                                                                     .orElseThrow(() -> new NotFoundException("No vocabulary found in context " + uriToString(ctx))), ctx));
    }

    /**
     * Gets the currently registered editable contexts.
     *
     * @return Set of context identifiers
     */
    public Set<URI> getCurrentlyEditedContexts() {
        return vocabularies.getRegisteredContexts();
    }
}

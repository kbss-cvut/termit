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
package cz.cvut.kbss.termit.service.language;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.exception.InvalidLanguageConstantException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A service that fetches parts of languages for the use in TermIt.
 * <p>
 * A language in this context means a formal set of concepts with well-defined meaning and relationships.
 */
@Service
public class LanguageService {

    private final DataRepositoryService dataService;

    private final UfoTermTypesService termTypesService;

    private final TermStateLanguageService termStatesService;

    public LanguageService(DataRepositoryService dataService, UfoTermTypesService termTypesService,
                           TermStateLanguageService termStatesService) {
        this.dataService = dataService;
        this.termTypesService = termTypesService;
        this.termStatesService = termStatesService;
    }

    /**
     * Gets all available term types.
     *
     * @return List of available term types
     */
    public List<Term> getTermTypes() {
        return termTypesService.getTypes();
    }

    /**
     * Gets resources representing access levels.
     * <p>
     * The resources correspond to individuals mapped by {@link cz.cvut.kbss.termit.model.acl.AccessLevel}.
     *
     * @return List of resources
     */
    public List<RdfsResource> getAccessLevels() {
        return Stream.of(AccessLevel.values()).map(al -> dataService.find(URI.create(al.getIri())))
                     .flatMap(Optional::stream)
                     .collect(Collectors.toList());
    }

    /**
     * Gets a list of possible states a term can have.
     *
     * @return List of resources representing available term states
     */
    public List<RdfsResource> getTermStates() {
        return termStatesService.getTermStates();
    }

    /**
     * Checks whether the specified identifier represents a known term state.
     *
     * @param state State identifier to validate
     * @throws InvalidLanguageConstantException If the specified identifier is not a state
     */
    public void verifyStateExists(URI state) {
        if (state != null && getTermStates().stream().noneMatch(u -> Objects.equals(u.getUri(), state))) {
            throw new InvalidLanguageConstantException("Unknown term state " + Utils.uriToString(state));
        }
    }

    /**
     * Gets the initial term state.
     * <p>
     * That is, gets the state that a new term should be assigned by default. Note that the state language may not
     * specify an initial state.
     *
     * @return Optional initial term state
     */
    public Optional<RdfsResource> getInitialTermState() {
        return termStatesService.getInitialState();
    }
}

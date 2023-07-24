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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageServiceTest {

    @Mock
    private DataRepositoryService dataService;

    @InjectMocks
    private LanguageService sut;

    @Test
    void getAccessLevelsRetrievesResourcesRepresentingEachOfAccessLevelConstants() {
        final RdfsResource res = new RdfsResource(URI.create(Vocabulary.s_i_cteni), new LangString("Read",
                Environment.LANGUAGE),
                null, null);
        when(dataService.find(any(URI.class))).thenReturn(Optional.of(res));

        final List<RdfsResource> result = sut.getAccessLevels();
        assertEquals(AccessLevel.values().length, result.size());
        Stream.of(AccessLevel.values()).forEach(al -> verify(dataService).find(URI.create(al.getIri())));
    }
}

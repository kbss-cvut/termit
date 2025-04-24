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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cz.cvut.kbss.termit.util.Constants.QueryParams.NAMESPACE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SnapshotControllerTest extends BaseControllerTestRunner {

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private IdentifierResolver idResolver;

    @InjectMocks
    private SnapshotController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void removeSnapshotLoadsSnapshotWithSpecifiedIdAndRemovesIt() throws Exception {
        final Snapshot snapshot = Generator.generateSnapshot(Generator.generateVocabularyWithId());
        final String localName = IdentifierResolver.extractIdentifierFragment(snapshot.getUri());
        final String namespace = IdentifierResolver.extractIdentifierNamespace(snapshot.getUri());
        when(idResolver.resolveIdentifier(namespace, localName)).thenReturn(snapshot.getUri());
        when(snapshotService.findRequired(snapshot.getUri())).thenReturn(snapshot);
        mockMvc.perform(delete(SnapshotController.PATH + "/" + localName).queryParam(NAMESPACE, namespace))
               .andExpect(status().isNoContent());

        verify(snapshotService).findRequired(snapshot.getUri());
        verify(snapshotService).remove(snapshot);
    }
}

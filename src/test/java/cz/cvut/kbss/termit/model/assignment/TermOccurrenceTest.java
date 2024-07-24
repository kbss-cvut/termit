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
package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TermOccurrenceTest {

    @Test
    void resolveContextCreatesDescriptorWithContextBasedOnSpecifiedSource() {
        final URI source = Generator.generateFileWithId("test.html").getUri();

        final URI ctx = TermOccurrence.resolveContext(source);
        assertThat(ctx.toString(), startsWith(source.toString()));
        assertThat(ctx.toString(), endsWith(TermOccurrence.CONTEXT_SUFFIX));
    }

    @Test
    void resolveContextSupportsSourceIdentifiersEndingWithSlash() {
        final URI source = URI.create(Generator.generateUri() + "/");
        final URI ctx = TermOccurrence.resolveContext(source);
        assertThat(ctx.toString(), startsWith(source.toString()));
        assertThat(ctx.toString(), endsWith("/" + TermOccurrence.CONTEXT_SUFFIX));
    }

    @Test
    void markSuggestedAddsSuggestedTypeToTypes() {
        final TermOccurrence sut = new TermFileOccurrence();
        sut.markSuggested();
        assertTrue(sut.isSuggested());
        assertThat(sut.getTypes(), hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu));
    }

    @Test
    void markApprovedRemovesSuggestedTypeFromTypes() {
        final TermOccurrence sut = new TermFileOccurrence();
        sut.addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
        assertTrue(sut.isSuggested());
        sut.markApproved();
        assertFalse(sut.isSuggested());
        assertThat(sut.getTypes(), not(hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
    }
}

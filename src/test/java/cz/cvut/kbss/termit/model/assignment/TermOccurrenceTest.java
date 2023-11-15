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
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

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
}

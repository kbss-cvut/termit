/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto.assignment;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceTermAssignmentsTest {

    @Test
    void constructorAddsAssignmentType() {
        final URI termUri = Generator.generateUri();
        final URI vocabularyUri = Generator.generateUri();
        final URI resourceUri = Generator.generateUri();
        final String label = "Test term";
        final ResourceTermAssignments result = new ResourceTermAssignments(termUri, label, vocabularyUri, resourceUri,
                false);
        assertNotNull(result);
        assertThat(result.getTypes(), hasItem(Vocabulary.s_c_prirazeni_termu));
    }

    @Test
    void constructorAddsSuggestedTypeWhenSuggestedIsTrue() {
        final URI termUri = Generator.generateUri();
        final URI vocabularyUri = Generator.generateUri();
        final URI resourceUri = Generator.generateUri();
        final String label = "Test term";
        final ResourceTermAssignments result = new ResourceTermAssignments(termUri, label, vocabularyUri, resourceUri,
                true);
        assertThat(result.getTypes(), hasItem(Vocabulary.s_c_prirazeni_termu));
        assertThat(result.getTypes(), hasItem(Vocabulary.s_c_navrzene_prirazeni_termu));
    }
}
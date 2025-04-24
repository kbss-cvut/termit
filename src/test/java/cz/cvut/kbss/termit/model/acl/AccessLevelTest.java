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
package cz.cvut.kbss.termit.model.acl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessLevelTest {

    @ParameterizedTest
    @MethodSource("accessLevelGenerator")
    void includesSupportsHierarchicalAccessLevels(AccessLevel sut, List<AccessLevel> expected) {
        expected.forEach(al -> assertTrue(sut.includes(al)));
    }

    static Stream<Arguments> accessLevelGenerator() {
        return Stream.of(
                Arguments.of(AccessLevel.NONE, List.of(AccessLevel.NONE)),
                Arguments.of(AccessLevel.READ, List.of(AccessLevel.NONE, AccessLevel.READ)),
                Arguments.of(AccessLevel.WRITE, List.of(AccessLevel.NONE, AccessLevel.READ, AccessLevel.WRITE)),
                Arguments.of(AccessLevel.SECURITY,
                             List.of(AccessLevel.NONE, AccessLevel.READ, AccessLevel.WRITE, AccessLevel.SECURITY))
        );
    }
}

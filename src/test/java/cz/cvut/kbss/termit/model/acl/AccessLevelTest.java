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

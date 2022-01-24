package cz.cvut.kbss.termit.environment.util;

import cz.cvut.kbss.termit.model.util.HasIdentifier;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Collection;
import java.util.Objects;

/**
 * Checks whether the provided collection contains the same entities as the expected one.
 * <p>
 * The membership check is done based on entity URIs.
 */
public class ContainsSameEntities extends TypeSafeMatcher<Collection<? extends HasIdentifier>> {

    private final Collection<? extends HasIdentifier> expected;

    public ContainsSameEntities(Collection<? extends HasIdentifier> expected) {
        this.expected = Objects.requireNonNull(expected);
    }

    @Override
    protected boolean matchesSafely(Collection<? extends HasIdentifier> actual) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (HasIdentifier e : expected) {
            if (actual.stream().noneMatch(ee -> Objects.equals(e.getUri(), ee.getUri()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValueList("[", ", ", "]", expected);
    }

    public static ContainsSameEntities containsSameEntities(Collection<? extends HasIdentifier> expected) {
        return new ContainsSameEntities(expected);
    }
}

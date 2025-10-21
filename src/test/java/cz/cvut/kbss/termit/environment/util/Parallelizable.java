package cz.cvut.kbss.termit.environment.util;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Marker interface for tests that can be run in parallel.
 */
@Execution(ExecutionMode.CONCURRENT)
public interface Parallelizable {
}

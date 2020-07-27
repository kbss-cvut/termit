package cz.cvut.kbss.termit.model.changetracking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks fields whose changes in an {@link Audited} instance should be ignored.
 * <p>
 * This allows to exclude certain attributes (e.g., provenance data) from change tracking.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IgnoreChanges {
}

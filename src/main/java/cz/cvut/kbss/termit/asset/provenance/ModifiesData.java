package cz.cvut.kbss.termit.asset.provenance;

import cz.cvut.kbss.termit.aspect.RefreshLastModifiedAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates methods which modify data and should thus refresh last modified date on completion.
 * <p>
 * This annotation should be used on methods of classes implementing {@link SupportsLastModification}. A corresponding
 * aspect defined in {@link RefreshLastModifiedAspect} refreshes the last modified timestamp when a method modifying
 * data is invoked.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModifiesData {
}

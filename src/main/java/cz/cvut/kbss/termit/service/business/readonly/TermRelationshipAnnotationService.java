package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.meta.AnnotatedTermRelationship;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.List;

/**
 * Manages annotations of term relationships.
 */
public interface TermRelationshipAnnotationService {

    /**
     * Gets annotations of all relationships whose subject is the specified term.
     * <p>
     * Note that for symmetric relationships, the specified term is always considered the subject.
     *
     * @param termId Term identifier
     * @return List of term relationship annotations
     */
    @Nonnull
    List<TermRelationshipAnnotation> findAllForSubject(@Nonnull URI termId);

    /**
     * Gets info about term relationships that are annotated by the specified term.
     *
     * @param termId Term identifier
     * @return List of annotated term relationships
     */
    @Nonnull
    List<AnnotatedTermRelationship> findAnnotatedRelationships(@Nonnull URI termId);

    /**
     * Updates annotation of the relationship specified by the provided {@link TermRelationshipAnnotation} instance.
     * <p>
     * Replaces any existing values of the specified term relationship annotation.
     *
     * @param termId     Subject term identifier
     * @param annotation Term relationship annotation
     */
    void updateAnnotation(@Nonnull URI termId, @Nonnull TermRelationshipAnnotation annotation);
}

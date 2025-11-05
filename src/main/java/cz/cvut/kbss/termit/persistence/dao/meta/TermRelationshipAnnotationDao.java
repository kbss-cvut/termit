package cz.cvut.kbss.termit.persistence.dao.meta;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

@Repository
public class TermRelationshipAnnotationDao {

    private static final List<URI> SYMMETRIC_SKOS_PROPERTIES = List.of(
            URI.create(SKOS.RELATED_MATCH),
            URI.create(SKOS.EXACT_MATCH),
            URI.create(SKOS.RELATED)
    );

    private final EntityManager em;

    private final VocabularyContextMapper vocabularyContextMapper;

    private final DataDao dataDao;

    public TermRelationshipAnnotationDao(EntityManager em, VocabularyContextMapper vocabularyContextMapper,
                                         DataDao dataDao) {
        this.em = em;
        this.vocabularyContextMapper = vocabularyContextMapper;
        this.dataDao = dataDao;
    }

    /**
     * Retrieves relationship annotations where the specified term is the annotated relationship's subject.
     *
     * @param term Term whose relationship annotations to get
     * @return List of annotations
     */
    @Nonnull
    public List<TermRelationshipAnnotation> findAllForSubject(@Nonnull Term term) {
        Objects.requireNonNull(term);
        final URI context = vocabularyContextMapper.getVocabularyContext(term.getVocabulary());
        return Stream.concat(findAnnotationsForSubject(term, context).stream(),
                             findAnnotationsForInverseSideOfSkosSymmetricProperties(term).stream()).toList();
    }

    private List<TermRelationshipAnnotation> findAnnotationsForSubject(Term term,
                                                                       URI context) {
        final List<CustomAttribute> annotationProperties = dataDao.findAllCustomAttributesByDomain(
                URI.create(RDF.STATEMENT));
        return (List<TermRelationshipAnnotation>) em.createNativeQuery(
                                                            "SELECT DISTINCT ?subject ?predicate ?object ?attribute ?value WHERE {" +
                                                                    "GRAPH ?g {" +
                                                                    "<< ?subject ?predicate ?object >> ?attribute ?value . }" +
                                                                    "}", "TermRelationshipAnnotation")
                                                    .setParameter("g", context)
                                                    .setParameter("subject", term)
                                                    .setParameter("attribute", annotationProperties)
                                                    .getResultStream()
                                                    .collect(new TermRelatioinshipAnnotationCollector());
    }

    /**
     * Retrieves annotations for the inverse side of symmetric SKOS properties.
     *
     * @param term Term whose relationship annotations to retrieve
     * @return List of resolved annotations
     */
    private List<TermRelationshipAnnotation> findAnnotationsForInverseSideOfSkosSymmetricProperties(Term term) {
        final List<CustomAttribute> annotationProperties = dataDao.findAllCustomAttributesByDomain(
                URI.create(RDF.STATEMENT));
        return (List<TermRelationshipAnnotation>) em.createNativeQuery(
                                                            "SELECT DISTINCT ?subject ?predicate ?object ?attribute ?value WHERE {" +
                                                                    "GRAPH ?g { << ?object ?predicate ?subject >> ?attribute ?value . }" +
                                                                    "FILTER (?attribute IN (?atts))" +
                                                                    "FILTER NOT EXISTS { ?object a ?termSnapshot . }" +
                                                                    "}", "TermRelationshipAnnotation")
                                                    .setParameter("subject", term)
                                                    .setParameter("predicate", SYMMETRIC_SKOS_PROPERTIES)
                                                    .setParameter("atts", annotationProperties)
                                                    .setParameter("termSnapshot",
                                                                  URI.create(Vocabulary.s_c_verze_pojmu))
                                                    .getResultStream()
                                                    .collect(new TermRelatioinshipAnnotationCollector());
    }

    /**
     * Combines instances of {@link TermRelationshipAnnotation} that share the same relationship and attribute.
     */
    private static class TermRelatioinshipAnnotationCollector
            implements Collector<TermRelationshipAnnotation, List<TermRelationshipAnnotation>, List<TermRelationshipAnnotation>> {

        @Override
        public Supplier<List<TermRelationshipAnnotation>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<List<TermRelationshipAnnotation>, TermRelationshipAnnotation> accumulator() {
            return (lst, ann) -> {
                if (lst.isEmpty()) {
                    lst.add(ann);
                }
                final TermRelationshipAnnotation lastItem = lst.get(lst.size() - 1);
                if (Objects.equals(lastItem.getRelationship(), ann.getRelationship()) && Objects.equals(
                        lastItem.getAttribute(), ann.getAttribute())) {
                    lastItem.getValue().addAll(ann.getValue());
                } else {
                    lst.add(ann);
                }
            };
        }

        @Override
        public BinaryOperator<List<TermRelationshipAnnotation>> combiner() {
            return (a, b) -> {
                a.addAll(b);
                return a;
            };
        }

        @Override
        public Function<List<TermRelationshipAnnotation>, List<TermRelationshipAnnotation>> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of(Characteristics.IDENTITY_FINISH);
        }
    }
}

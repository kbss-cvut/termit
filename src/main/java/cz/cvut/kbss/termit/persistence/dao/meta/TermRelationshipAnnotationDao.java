package cz.cvut.kbss.termit.persistence.dao.meta;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.ontodriver.rdf4j.util.Rdf4jUtils;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.exception.TermRelationshipAnnotationException;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.util.Pair;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(TermRelationshipAnnotationDao.class);

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

    private List<TermRelationshipAnnotation> findAnnotationsForSubject(Term term, URI context) {
        final List<CustomAttribute> annotationProperties = dataDao.findAllCustomAttributesByDomain(
                URI.create(RDF.STATEMENT));
        return (List<TermRelationshipAnnotation>) em.createNativeQuery(
                                                            """
                                                                    SELECT DISTINCT ?subject ?predicate ?object ?attribute ?value WHERE {
                                                                    GRAPH ?g {
                                                                    << ?subject ?predicate ?object >> ?attribute ?value . }
                                                                    }""", "TermRelationshipAnnotation")
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
                                                            """
                                                                    SELECT DISTINCT ?subject ?predicate ?object ?attribute ?value WHERE {
                                                                    GRAPH ?g { << ?object ?predicate ?subject >> ?attribute ?value . }
                                                                    FILTER (?attribute IN (?atts))
                                                                    FILTER NOT EXISTS { ?object a ?termSnapshot . }
                                                                    }""", "TermRelationshipAnnotation")
                                                    .setParameter("subject", term)
                                                    .setParameter("predicate", SYMMETRIC_SKOS_PROPERTIES)
                                                    .setParameter("atts", annotationProperties)
                                                    .setParameter("termSnapshot",
                                                                  URI.create(Vocabulary.s_c_verze_pojmu))
                                                    .getResultStream()
                                                    .collect(new TermRelatioinshipAnnotationCollector());
    }

    /**
     * Sets the values of the specified term relationship annotation.
     * <p>
     * This method resolves the correct repository context and stores the relationship annotation in the repository.
     *
     * @param annotation Annotation with values to set
     */
    public void updateTermRelationshipAnnotation(@Nonnull TermRelationshipAnnotation annotation) {
        Objects.requireNonNull(annotation);
        final Pair<RdfStatement, URI> actualStatementAndContext = resolveActualContextAndStatement(
                annotation.getRelationship());

        removeExistingValues(actualStatementAndContext, annotation.getAttribute());
        insertAnnotations(actualStatementAndContext.getFirst(), annotation.getAttribute(), annotation.getValue(),
                          actualStatementAndContext.getSecond());
    }

    /**
     * Resolves the actual explicit assertion of the specified term relationship, which can possibly be an inferred
     * inverse side of a symmetric SKOS property assertion.
     * <p>
     * For this explicit assertion, the repository context is also resolved.
     *
     * @param statement Statement to resolve
     * @return Actual asserted statement and its repository context
     */
    private Pair<RdfStatement, URI> resolveActualContextAndStatement(RdfStatement statement) {
        try {
            final Object result = em.createNativeQuery("SELECT ?ss ?p ?oo ?g WHERE {" +
                                                               "{" +
                                                               "GRAPH ?g {" +
                                                               "?s ?p ?o ." +
                                                               "BIND(?s AS ?ss)" +
                                                               "BIND(?o AS ?oo)" +
                                                               "} } UNION {" +
                                                               "GRAPH ?g {" +
                                                               "?o ?p ?s ." +
                                                               "BIND(?o AS ?ss)" +
                                                               "BIND(?s AS ?oo)" +
                                                               "} } }")
                                    .setParameter("s", statement.getSubject())
                                    .setParameter("p", statement.getRelation())
                                    .setParameter("o", statement.getObject()).getSingleResult();
            assert result instanceof Object[];
            final Object[] resultRow = (Object[]) result;
            assert resultRow.length == 4;
            return new Pair<>(new RdfStatement((URI) resultRow[0], (URI) resultRow[1], (URI) resultRow[2]),
                              (URI) resultRow[3]);
        } catch (NoResultException e) {
            LOG.error("Could not find statement {} or its inverse when resolving term relationship annotation context.",
                      statement, e);
            throw new TermRelationshipAnnotationException("Did not find statement " + statement + " or its inverse.");
        }
    }

    /**
     * Removes existing values of a term relationship annotation.
     *
     * @param annotatedStatement Annotated term relationship statement
     * @param annotationProperty Annotation property
     */
    private void removeExistingValues(Pair<RdfStatement, URI> annotatedStatement, URI annotationProperty) {
        em.createNativeQuery("DELETE WHERE {" +
                                     "GRAPH ?g {" +
                                     "<< ?subject ?predicate ?object >> ?annotationProperty ?value ." +
                                     "} }")
          .setParameter("g", annotatedStatement.getSecond())
          .setParameter("subject", annotatedStatement.getFirst().getSubject())
          .setParameter("predicate", annotatedStatement.getFirst().getRelation())
          .setParameter("object", annotatedStatement.getFirst().getObject())
          .setParameter("annotationProperty", annotationProperty)
          .executeUpdate();
    }

    /**
     * Inserts new values of the specified term relationship annotation.
     *
     * @param subject  Term relationship statement being annotated
     * @param property Annotation property
     * @param values   Values of the annotation
     * @param context  Target repository context
     */
    private void insertAnnotations(RdfStatement subject, URI property, Set<Object> values, URI context) {
        final org.eclipse.rdf4j.repository.Repository repo = em.unwrap(org.eclipse.rdf4j.repository.Repository.class);
        final ValueFactory vf = repo.getValueFactory();
        final IRI annProperty = vf.createIRI(property.toString());
        final Triple subjTriple = vf.createTriple(vf.createIRI(subject.getSubject().toString()),
                                                  vf.createIRI(subject.getRelation().toString()),
                                                  vf.createIRI(subject.getObject().toString()));
        final IRI contextIri = vf.createIRI(context.toString());
        try (final RepositoryConnection conn = repo.getConnection()) {
            conn.begin();
            for (final Object value : values) {
                final Value val = value instanceof URI ? vf.createIRI(value.toString()) :
                                  Rdf4jUtils.createLiteral(value, null, vf);
                conn.add(subjTriple, annProperty, val, contextIri);
            }
            conn.commit();
        }
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

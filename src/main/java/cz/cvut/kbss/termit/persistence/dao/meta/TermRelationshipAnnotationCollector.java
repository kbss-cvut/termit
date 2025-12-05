package cz.cvut.kbss.termit.persistence.dao.meta;

import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Combines instances of {@link TermRelationshipAnnotation} that share the same relationship and attribute.
 */
class TermRelationshipAnnotationCollector
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

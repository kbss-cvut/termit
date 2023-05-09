package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;
import cz.cvut.kbss.termit.model.AbstractEntity_;

import java.net.URI;

@StaticMetamodel(TermOccurrence.class)
public abstract class TermOccurrence_ extends AbstractEntity_ {

    public static volatile SingularAttribute<TermOccurrence, URI> term;

    public static volatile SingularAttribute<TermOccurrence, OccurrenceTarget> target;

    public static volatile SingularAttribute<TermOccurrence, String> description;

    public static volatile TypesSpecification<TermOccurrence, String> types;
}

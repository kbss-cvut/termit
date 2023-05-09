package cz.cvut.kbss.termit.model.assignment;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.AbstractEntity_;
import cz.cvut.kbss.termit.model.selector.Selector;

import java.net.URI;

@StaticMetamodel(OccurrenceTarget.class)
public abstract class OccurrenceTarget_ extends AbstractEntity_ {

    public static volatile SingularAttribute<OccurrenceTarget, URI> source;

    public static volatile SetAttribute<OccurrenceTarget, Selector> selectors;
}

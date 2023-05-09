package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.AbstractEntity_;
import cz.cvut.kbss.termit.model.User;

import java.net.URI;
import java.time.Instant;

@StaticMetamodel(AbstractChangeRecord.class)
public abstract class AbstractChangeRecord_ extends AbstractEntity_ {

    public static volatile SingularAttribute<AbstractChangeRecord, Instant> timestamp;

    public static volatile SingularAttribute<AbstractChangeRecord, User> author;

    public static volatile SingularAttribute<AbstractChangeRecord, URI> changedEntity;
}

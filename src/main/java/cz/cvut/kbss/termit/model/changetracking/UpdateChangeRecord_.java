package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

import java.net.URI;

@StaticMetamodel(UpdateChangeRecord.class)
public abstract class UpdateChangeRecord_ extends AbstractChangeRecord_ {

    public static volatile SingularAttribute<UpdateChangeRecord, URI> changedAttribute;

    public static volatile SetAttribute<UpdateChangeRecord, Object> originalValue;

    public static volatile SetAttribute<UpdateChangeRecord, Object> newValue;
}

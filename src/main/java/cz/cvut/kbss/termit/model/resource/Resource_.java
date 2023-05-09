package cz.cvut.kbss.termit.model.resource;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.Asset_;

@StaticMetamodel(Resource.class)
public abstract class Resource_ extends Asset_ {

    public static volatile SingularAttribute<Resource, String> label;

    public static volatile SingularAttribute<Resource, String> description;
}

package cz.cvut.kbss.termit.model.resource;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;

@StaticMetamodel(File.class)
public abstract class File_ extends Resource_{

    public static volatile SingularAttribute<File, Document> document;

    public static volatile TypesSpecification<File, String> types;
}

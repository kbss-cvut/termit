package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;

@StaticMetamodel(User.class)
public abstract class User_ extends AccessControlAgent_ {

    public static volatile SingularAttribute<User, String> firstName;

    public static volatile SingularAttribute<User, String> lastName;

    public static volatile SingularAttribute<User, String> username;

    public static volatile TypesSpecification<User, String> types;
}

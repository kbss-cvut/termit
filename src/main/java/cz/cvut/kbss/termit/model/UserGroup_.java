package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

@StaticMetamodel(UserGroup.class)
public abstract class UserGroup_ extends AccessControlAgent_ {

    public static volatile SingularAttribute<UserGroup, String> label;

    public static volatile SetAttribute<UserGroup, User> members;
}

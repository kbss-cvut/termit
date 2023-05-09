package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;

@StaticMetamodel(UserRole.class)
public abstract class UserRole_ extends AccessControlAgent_ {

    public static volatile SingularAttribute<UserRole, MultilingualString> label;

    public static volatile SingularAttribute<UserRole, MultilingualString> description;
}

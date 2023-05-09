package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.metamodel.SingularAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.AbstractEntity_;
import cz.cvut.kbss.termit.model.AccessControlAgent;

@StaticMetamodel(AccessControlRecord.class)
public abstract class AccessControlRecord_ extends AbstractEntity_ {

    public static volatile SingularAttribute<AccessControlRecord, AccessLevel> accessLevel;

    public static volatile SingularAttribute<AccessControlRecord, AccessControlAgent> holder;
}

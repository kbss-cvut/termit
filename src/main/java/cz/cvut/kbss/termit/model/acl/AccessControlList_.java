package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.metamodel.SetAttribute;
import cz.cvut.kbss.jopa.model.metamodel.StaticMetamodel;
import cz.cvut.kbss.termit.model.AbstractEntity_;

@StaticMetamodel(AccessControlList.class)
public abstract class AccessControlList_ extends AbstractEntity_ {

    public static volatile SetAttribute<AccessControlList, AccessControlRecord> records;
}

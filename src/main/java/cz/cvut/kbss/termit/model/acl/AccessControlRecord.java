package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.model.AbstractEntity;

public abstract class AccessControlRecord extends AbstractEntity {

    private AccessLevel accessLevel;

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}

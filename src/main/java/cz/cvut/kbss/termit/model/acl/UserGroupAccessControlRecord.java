package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.model.UserGroup;

public class UserGroupAccessControlRecord extends AccessControlRecord {

    private UserGroup group;

    public UserGroup getGroup() {
        return group;
    }

    public void setGroup(UserGroup group) {
        this.group = group;
    }
}

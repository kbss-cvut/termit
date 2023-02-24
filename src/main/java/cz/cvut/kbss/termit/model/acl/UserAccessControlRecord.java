package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.model.User;

public class UserAccessControlRecord extends AccessControlRecord {

    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.model.UserRole;

public class RoleAccessControlRecord extends AccessControlRecord {

    private UserRole role;

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}

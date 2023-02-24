package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.model.AbstractEntity;

import java.util.Set;

public class AccessControlList extends AbstractEntity {

    private Set<AccessControlRecord> records;

    public Set<AccessControlRecord> getRecords() {
        return records;
    }

    public void setRecords(Set<AccessControlRecord> records) {
        this.records = records;
    }
}

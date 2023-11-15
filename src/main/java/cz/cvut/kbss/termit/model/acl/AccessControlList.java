/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Instances of this class specify the levels of access various
 */
@OWLClass(iri = Vocabulary.s_c_seznam_rizeni_pristupu)
public class AccessControlList extends AbstractEntity {

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zaznam_rizeni_pristupu, fetch = FetchType.EAGER,
                       cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private Set<AccessControlRecord<?>> records;

    public Set<AccessControlRecord<?>> getRecords() {
        return records;
    }

    public void setRecords(Set<AccessControlRecord<?>> records) {
        this.records = records;
    }

    public void addRecord(AccessControlRecord<?> record) {
        Objects.requireNonNull(record);
        if (records == null) {
            this.records = new HashSet<>();
        }
        records.add(record);
    }

    @Override
    public String toString() {
        return "AccessControlList{" + Utils.uriToString(getUri()) +
                " records=" + records +
                '}';
    }
}

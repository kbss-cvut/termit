package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.AttributeChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.OrderedCollectionMembersUpdateChangeRecord;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection_;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class CollectionMemberChangeCalculator implements ChangeCalculator {
    @Override
    public Collection<AttributeChangeRecord<?>> calculateChanges(Asset<?> changed, Asset<?> original) {
        if (changed instanceof OrderedTermCollection oChanged && original instanceof OrderedTermCollection oOriginal) {
            return calculateChanges(oChanged, oOriginal);
        }
        if (changed instanceof OrderedTermCollection || original instanceof OrderedTermCollection) {
            throw new IllegalArgumentException("Unable to calculate changes between different types: "
                    + changed.getClass().getName() + " " + original.getClass().getName());
        }
        return Collections.emptyList();
    }

    private Collection<AttributeChangeRecord<?>> calculateChanges(
            OrderedTermCollection changed, OrderedTermCollection original) {
        if (changed.getMembers().equals(original.getMembers())) {
            return Collections.emptyList();
        }

        OrderedCollectionMembersUpdateChangeRecord record = new OrderedCollectionMembersUpdateChangeRecord(changed);
        record.setChangedAttribute(OrderedTermCollection_.membersPropertyIRI.toURI());
        record.setOriginalValue(List.copyOf(original.getMembers()));
        record.setNewValue(List.copyOf(changed.getMembers()));
        return List.of(record);
    }
}

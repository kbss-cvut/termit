package cz.cvut.kbss.termit.service.repository.customization;

import cz.cvut.kbss.termit.event.AssetChangesRecordedEvent;
import cz.cvut.kbss.termit.event.TermReferencesUpdatedEvent;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.context.AssetContextMapper;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import cz.cvut.kbss.termit.persistence.dao.spec.CustomAttributeSpecifications;
import cz.cvut.kbss.termit.persistence.dao.util.Quad;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SymmetricCustomAttributeSynchronizer {

    private final DataDao dataDao;

    private final AssetContextMapper contextMapper;

    private final ApplicationEventPublisher eventPublisher;

    public SymmetricCustomAttributeSynchronizer(
            DataDao dataDao, AssetContextMapper contextMapper, ApplicationEventPublisher eventPublisher) {
        this.dataDao = dataDao;
        this.contextMapper = contextMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @EventListener
    public void onAssetChangeRecorded(AssetChangesRecordedEvent<?> event) {
        final List<UpdateChangeRecord> updates = event.getChangeRecords().stream()
                .filter(record -> record instanceof UpdateChangeRecord)
                .map(UpdateChangeRecord.class::cast)
                .toList();
        if (updates.isEmpty()) {
            return;
        }
        final Set<URI> symmetricCustomAtts =
                dataDao.findAllCustomAttributes(List.of(CustomAttributeSpecifications.isSymmetric())).stream()
                        .map(RdfsResource::getUri)
                        .collect(Collectors.toSet());
        updates.stream()
                .filter(record -> symmetricCustomAtts.contains(record.getChangedAttribute()))
                .forEach(record -> {
                    assert Utils.emptyIfNull(record.getOriginalValue()).stream().allMatch(o -> o instanceof URI);
                    assert Utils.emptyIfNull(record.getNewValue()).stream().allMatch(o -> o instanceof URI);
                    final Set<URI> original = Utils.emptyIfNull(record.getOriginalValue()).stream()
                            .map(URI.class::cast)
                            .collect(Collectors.toSet());
                    final Set<URI> update = Utils.emptyIfNull(record.getNewValue()).stream()
                            .map(URI.class::cast)
                            .collect(Collectors.toSet());
                    final Set<URI> addedValues = new HashSet<>(update);
                    addedValues.removeAll(original);
                    dataDao.insertRawData(createInverseAssertions(
                                    record.getChangedEntity(), record.getChangedAttribute(), addedValues)
                            .peek(q -> eventPublisher.publishEvent(new TermReferencesUpdatedEvent(
                                    this,
                                    q.subject(),
                                    record.getChangedAttribute().toString(),
                                    q.context())))
                            .toList());
                    final Set<URI> removedValues = new HashSet<>(original);
                    removedValues.removeAll(update);
                    dataDao.removeRawData(createInverseAssertions(
                                    record.getChangedEntity(), record.getChangedAttribute(), removedValues)
                            .peek(q -> eventPublisher.publishEvent(new TermReferencesUpdatedEvent(
                                    this,
                                    q.subject(),
                                    record.getChangedAttribute().toString(),
                                    q.context())))
                            .toList());
                });
    }

    private Stream<Quad> createInverseAssertions(URI subject, URI property, Set<URI> values) {
        return values.stream().map(value -> {
            final URI ctx = contextMapper.getContext(value);
            return new Quad(value, property, subject, ctx);
        });
    }
}

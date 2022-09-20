package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.persistence.dao.SnapshotDao;
import cz.cvut.kbss.termit.persistence.snapshot.SnapshotRemover;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Objects;

@Service
public class SnapshotRepositoryService implements SnapshotService {

    private final SnapshotDao dao;

    private final SnapshotRemover remover;

    public SnapshotRepositoryService(SnapshotDao dao, SnapshotRemover remover) {
        this.dao = dao;
        this.remover = remover;
    }

    @Transactional(readOnly = true)
    @Override
    public Snapshot findRequired(URI id) {
        return dao.find(id).orElseThrow(() -> NotFoundException.create(Snapshot.class, id));
    }

    @Transactional
    @Override
    public void remove(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        remover.removeSnapshot(snapshot);
    }
}

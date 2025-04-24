/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.persistence.dao.SnapshotDao;
import cz.cvut.kbss.termit.persistence.snapshot.SnapshotRemover;
import cz.cvut.kbss.termit.service.business.SnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@snapshotAuthorizationService.canRemove(#snapshot)")
    public void remove(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        remover.removeSnapshot(snapshot);
    }
}

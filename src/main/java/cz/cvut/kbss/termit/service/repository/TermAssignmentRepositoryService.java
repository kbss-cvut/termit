/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.assignment.ResourceTermAssignments;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.Target;
import cz.cvut.kbss.termit.model.assignment.TermAssignment;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.TargetDao;
import cz.cvut.kbss.termit.persistence.dao.TermAssignmentDao;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;

import static cz.cvut.kbss.termit.util.Constants.SCHEDULING_PATTERN;

@Service
public class TermAssignmentRepositoryService implements TermOccurrenceService {

    private static final Logger LOG = LoggerFactory.getLogger(TermAssignmentRepositoryService.class);

    private final TermAssignmentDao termAssignmentDao;

    private final TargetDao targetDao;

    private final TermDao termDao;

    private final TermOccurrenceDao termOccurrenceDao;

    @Autowired
    public TermAssignmentRepositoryService(TermAssignmentDao termAssignmentDao,
                                           TargetDao targetDao, TermDao termDao, TermOccurrenceDao termOccurrenceDao) {
        this.termAssignmentDao = termAssignmentDao;
        this.targetDao = targetDao;
        this.termDao = termDao;
        this.termOccurrenceDao = termOccurrenceDao;
    }

    /**
     * Gets aggregated information about Term assignments/occurrences for the specified Resource.
     *
     * @param resource Resource to get data for
     * @return List of aggregate term assignment data
     */
    public List<ResourceTermAssignments> getResourceAssignmentInfo(Resource resource) {
        Objects.requireNonNull(resource);
        return termAssignmentDao.getAssignmentInfo(resource);
    }

    /**
     * Gets term assignments of the specified Resource.
     *
     * @param resource Resource for which assignments will be loaded
     * @return List of retrieved assignments
     */
    public List<TermAssignment> findAll(Resource resource) {
        Objects.requireNonNull(resource);
        return termAssignmentDao.findAll(resource);
    }

    /**
     * Removes all assignments to the specified Resource.
     *
     * @param resource Resource for which assignments will be removed
     */
    @Transactional
    public void removeAll(Resource resource) {
        Objects.requireNonNull(resource);
        final Optional<Target> target = targetDao.findByWholeResource(resource);
        target.ifPresent(t -> {
            LOG.trace("Removing term assignments to resource {}.", resource);
            final List<TermAssignment> assignments = termAssignmentDao.findByTarget(t);
            assignments.forEach(termAssignmentDao::remove);
            targetDao.remove(t);
        });
    }

    /**
     * Creates assignments for terms with the specified identifiers and sets them on the specified Resource.
     * <p>
     * If there already exists assignments on the resource, those representing Terms not found in the specified set will
     * be removed. Those representing Terms in the specified set will remain and no new assignments will be added for
     * them.
     *
     * @param resource Target Resource
     * @param termUris Identifiers of Terms to assign
     */
    @Transactional
    public void setOnResource(Resource resource, Collection<URI> termUris) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(termUris);
        LOG.trace("Setting tags {} on resource {}.", termUris, resource);

        mergeAssignments(resource, termUris, true, false);

        LOG.trace("Finished setting tags on resource {}.", resource);
    }

    private void mergeAssignments(Resource resource, Collection<URI> termUris, boolean removeObsolete,
                                  boolean suggested) {
        if (!removeObsolete && termUris.isEmpty()) {
            return;
        }
        // get the whole-resource target
        final Target target = targetForResource(resource);

        // remove obsolete existing term assignments and determine new assignments to add
        final List<TermAssignment> termAssignments = termAssignmentDao.findByTarget(target);
        final Collection<URI> toAdd = new HashSet<>(termUris);
        final List<TermAssignment> toRemove = new ArrayList<>(termAssignments.size());
        for (TermAssignment existing : termAssignments) {
            if (!termUris.contains(existing.getTerm())) {
                toRemove.add(existing);
            } else {
                toAdd.remove(existing.getTerm());
            }
        }
        if (removeObsolete) {
            toRemove.forEach(termAssignmentDao::remove);
        }

        // create term assignments for each input term to the target
        createAssignments(target, toAdd, suggested);
    }

    private Target targetForResource(Resource resource) {
        return targetDao.findByWholeResource(resource).orElseGet(() -> {
            final Target target2 = new Target(resource);
            targetDao.persist(target2);
            return target2;
        });
    }

    private void createAssignments(Target target, Collection<URI> termUris, boolean suggested) {
        termUris.forEach(iTerm -> {
            if (!termDao.exists(iTerm)) {
                throw NotFoundException.create(Term.class.getSimpleName(), iTerm);
            }

            final TermAssignment termAssignment = new TermAssignment(iTerm, target);
            if (suggested) {
                termAssignment.addType(Vocabulary.s_c_navrzene_prirazeni_termu);
            }
            termAssignmentDao.persist(termAssignment);
        });
    }

    /**
     * Creates assignments for terms with the specified identifiers and adds them to the specified Resource.
     * <p>
     * This method does not remove any existing assignments. It only adds new ones for Terms which are not yet assigned
     * to the Resource.
     *
     * @param resource Target Resource
     * @param termUris Identifiers of Terms to assign
     * @see #setOnResource(Resource, Collection)
     */
    @Transactional
    public void addToResource(Resource resource, Collection<URI> termUris) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(termUris);
        LOG.trace("Adding tags {} to resource {}.", termUris, resource);

        mergeAssignments(resource, termUris, false, false);

        LOG.trace("Finished adding tags to resource {}.", resource);
    }

    /**
     * Creates assignments for terms with the specified identifiers and adds them to the specified Resource as
     * "suggested".
     * <p>
     * Suggested terms may be treated differently by the application because they are usually created by an automated
     * service, without the user's intervention.
     * <p>
     * This method does not remove any existing assignments. It only adds new ones for Terms which are not yet assigned
     * to the Resource.
     *
     * @param resource Target Resource
     * @param termUris Identifiers of Terms to assign
     * @see #addToResource(Resource, Collection)
     */
    @Transactional
    public void addToResourceSuggested(Resource resource, Collection<URI> termUris) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(termUris);
        LOG.trace("Adding suggested tags {} to resource {}.", termUris, resource);

        mergeAssignments(resource, termUris, false, true);

        LOG.trace("Finished adding suggested tags to resource {}.", resource);
    }

    @Transactional
    @Override
    public void persistOccurrence(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        termOccurrenceDao.persist(occurrence);
    }

    @Transactional
    @Override
    public void approveOccurrence(URI identifier) {
        Objects.requireNonNull(identifier);
        LOG.trace("approve term occurrence with identifier {}", identifier);
        Optional<TermOccurrence> occurrence = termOccurrenceDao.find(identifier);
        occurrence.ifPresent(o -> {
            o.removeType(cz.cvut.kbss.termit.util.Vocabulary.s_c_navrzeny_vyskyt_termu);
            termOccurrenceDao.update(o);
        });
    }

    @Transactional
    @Override
    public void removeOccurrence(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        LOG.trace("remove term occurrence with identifier {}", occurrence);
        termOccurrenceDao.remove(occurrence);
    }

    @Override
    public TermOccurrence getRequiredReference(URI id) {
        return termOccurrenceDao.getReference(id).orElseThrow(() ->
                NotFoundException.create(TermOccurrence.class.getSimpleName(), id)
        );
    }

    /**
     * Cleans up possibly orphaned term occurrences.
     *
     * Such occurrences reference targets whose sources no longer exist in the repository.
     */
    @Scheduled(cron = SCHEDULING_PATTERN)
    @Transactional
    public void cleanupOrphans() {
        LOG.debug("Executing orphaned term occurrences cleanup.");
        termOccurrenceDao.removeAllOrphans();
    }
}

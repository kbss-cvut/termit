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
package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

@Service
public class VocabularyAccessControlListGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(VocabularyAccessControlListGenerator.class);

    private final EntityManager em;

    private final VocabularyRepositoryService vocabularyService;

    private final AccessControlListService aclService;

    public VocabularyAccessControlListGenerator(EntityManager em,
                                                VocabularyRepositoryService vocabularyService,
                                                AccessControlListService aclService) {
        this.em = em;
        this.vocabularyService = vocabularyService;
        this.aclService = aclService;
    }

    /**
     * Creates {@link cz.cvut.kbss.termit.model.acl.AccessControlList}s for {@link cz.cvut.kbss.termit.model.Vocabulary}s
     * that do not have them.
     * <p>
     * This is basically a data migration method that ensures all vocabularies have an ACL. It may be removed in the
     * next major release of TermIt when it is deemed not necessary.
     * <p>
     * This method is asynchronous to prevent slowing down the system startup.
     */
    @Async
    @Transactional
    public void generateMissingAccessControlLists() {
        LOG.debug("Generating missing vocabulary access control lists (ACLs).");
        final List<URI> vocabsWithoutAcl = resolveVocabulariesWithoutAcl();
        LOG.trace("Generating access control lists for vocabularies: {}.", vocabsWithoutAcl);
        vocabsWithoutAcl.forEach(vUri -> {
            final cz.cvut.kbss.termit.model.Vocabulary v = vocabularyService.findRequired(vUri);
            final AccessControlList acl = aclService.createFor(v);
            v.setAcl(acl.getUri());
        });
        LOG.trace("Finished generating {} vocabulary ACLs.", vocabsWithoutAcl.size());
    }

    private List<URI> resolveVocabulariesWithoutAcl() {
        return em.createNativeQuery(
                         "SELECT DISTINCT ?v WHERE { ?v a ?vocabulary . FILTER NOT EXISTS { ?v ?hasAcl ?acl . } }", URI.class)
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                 .setParameter("hasAcl", URI.create(Vocabulary.s_p_ma_seznam_rizeni_pristupu))
                 .getResultList();
    }
}

/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetDao assetDao;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private AssetService sut;

    @AfterEach
    void tearDown() {
        Environment.resetCurrentUser();
    }

    private List<RecentlyModifiedAsset> generateRecentlyModifiedAssets(int count) {
        final List<RecentlyModifiedAsset> assets = new ArrayList<>();
        final User author = Generator.generateUserWithId();
        for (int i = 0; i < count; i++) {
            RecentlyModifiedAsset rma = null;
            switch (i % 3) {
                case 0:
                    final Resource resource = Generator.generateResourceWithId();
                    rma = new RecentlyModifiedAsset(resource.getUri(), resource.getLabel(), Utils.timestamp(),
                                                    author.getUri(), null,
                                                    cz.cvut.kbss.termit.util.Vocabulary.s_c_resource,
                                                    Vocabulary.s_c_vytvoreni_entity);
                    break;
                case 1:
                    final Term term = Generator.generateTermWithId();
                    rma = new RecentlyModifiedAsset(term.getUri(), term.getLabel().get(Environment.LANGUAGE),
                                                    Utils.timestamp(), author.getUri(), Generator.generateUri(),
                                                    SKOS.CONCEPT, Vocabulary.s_c_vytvoreni_entity);
                    break;
                case 2:
                    final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
                    rma = new RecentlyModifiedAsset(vocabulary.getUri(), vocabulary.getLabel(), Utils.timestamp(),
                                                    author.getUri(), null,
                                                    Vocabulary.s_c_slovnik, Vocabulary.s_c_vytvoreni_entity);
                    break;
            }
            rma.setModified(Instant.ofEpochMilli(System.currentTimeMillis() - i * 1000L));
            rma.setEditor(author);
            assets.add(rma);
        }
        return assets;
    }

    @Test
    void findLastEditedReturnsRecentlyEditedAssets() {
        when(vocabularyAuthorizationService.canRead(any(cz.cvut.kbss.termit.model.Vocabulary.class))).thenReturn(true);
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(6);
        when(assetDao.findLastEdited(any(Pageable.class))).thenReturn(new PageImpl<>(allExpected));
        final PageRequest pageSpec = PageRequest.of(0, 10);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(allExpected, result.getContent());
        verify(assetDao).findLastEdited(pageSpec);
    }

    @Test
    void findMyLastEditedGetsLastEditedByCurrentUser() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(15);
        when(assetDao.findLastEditedBy(any(User.class), any(Pageable.class))).thenReturn(new PageImpl<>(allExpected));
        final UserAccount currentUser = Generator.generateUserAccount();
        Environment.setCurrentUser(currentUser);

        final PageRequest pageSpec = PageRequest.of(0, 10);
        final Page<RecentlyModifiedAsset> result = sut.findMyLastEdited(pageSpec);
        assertEquals(allExpected, result.getContent());
        verify(assetDao).findLastEditedBy(currentUser.toUser(), pageSpec);
    }

    private List<RecentlyCommentedAsset> generateRecentlyCommentedAssets() {
        final List<RecentlyCommentedAsset> assets = new ArrayList<>();
        final User author = Generator.generateUserWithId();
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final Term term = Generator.generateTermWithId();
            Comment comment = Generator.generateComment(author, term);
            RecentlyCommentedAsset rca = new RecentlyCommentedAsset(term.getUri(), comment.getUri(), null,
                                                                    SKOS.CONCEPT);
            comment.setCreated(Instant.ofEpochMilli(System.currentTimeMillis() - i * 1000L));
            comment.setAuthor(author);
            comment.setAsset(term.getUri());
            rca.setLastComment(comment);
            assets.add(rca);
        }
        return assets;
    }

    @Test
    void findLastCommentedReturnsAssetsSortedByDateCommentCreatedDescending() {
        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets();
        allExpected.sort(
                Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        when(termService.findLastCommented(any(Pageable.class))).thenReturn(new PageImpl<>(allExpected));
        final Page<RecentlyCommentedAsset> result = sut.findLastCommented(PageRequest.of(0, 10));
        assertEquals(allExpected, result.getContent());
    }

    @Test
    void findMyLastCommentedReturnsAssetsSortedByDateCommentCreatedDescending() {
        final UserAccount currentUser = Generator.generateUserAccount();
        Environment.setCurrentUser(currentUser);

        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets();
        allExpected.sort(
                Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        when(termService.findMyLastCommented(any(User.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(allExpected));
        final Page<RecentlyCommentedAsset> result = sut.findMyLastCommented(PageRequest.of(0, 10));
        assertEquals(allExpected, result.getContent());
    }

    @Test
    void findLastCommentedInReactionToMineReturnsAssetsSortedByDateCommentCreatedDescending() {
        final UserAccount currentUser = Generator.generateUserAccount();
        Environment.setCurrentUser(currentUser);

        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets();
        allExpected.sort(
                Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        when(termService.findLastCommentedInReaction(any(User.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(allExpected));
        final Page<RecentlyCommentedAsset> result = sut.findLastCommentedInReactionToMine(PageRequest.of(0, 10));
        assertEquals(allExpected, result.getContent());
    }

    @Test
    void findLastEditedMasksLabelsOfAssetsThatCurrentUserIsNotAuthorizedToRead() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(6);
        when(assetDao.findLastEdited(any(Pageable.class))).thenReturn(new PageImpl<>(allExpected));
        allExpected.forEach(ra -> {
            if (ra.hasType(SKOS.CONCEPT)) {
                when(vocabularyAuthorizationService.canRead(
                        new cz.cvut.kbss.termit.model.Vocabulary(ra.getVocabulary()))).thenReturn(false);
            } else {
                when(vocabularyAuthorizationService.canRead(
                        new cz.cvut.kbss.termit.model.Vocabulary(ra.getUri()))).thenReturn(true);
            }
        });

        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(Constants.DEFAULT_PAGE_SPEC);
        assertEquals(allExpected.size(), result.getSize());
        result.get().filter(ra -> ra.hasType(SKOS.CONCEPT))
              .forEach(ra -> assertEquals(AssetService.MASK, ra.getLabel()));
    }
}

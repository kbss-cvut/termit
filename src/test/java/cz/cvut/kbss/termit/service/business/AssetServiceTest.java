/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetServiceTest {

    @Mock
    private ResourceRepositoryService resourceService;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private VocabularyRepositoryService vocabularyService;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private AssetService sut;

    @Test
    void findRecentlyEditedCombinesOutputOfAllAssetServices() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(15);

        final int count = allExpected.size();
        final List<RecentlyModifiedAsset> result = sut.findLastEdited(count);
        assertEquals(count, result.size());
        assertTrue(allExpected.containsAll(result));
        verify(resourceService).findLastEdited(count);
        verify(termService).findLastEdited(count);
        verify(vocabularyService).findLastEdited(count);
    }

    private List<RecentlyModifiedAsset> generateRecentlyModifiedAssets(int count) {
        final List<RecentlyModifiedAsset> assets = new ArrayList<>();
        final List<RecentlyModifiedAsset> resources = new ArrayList<>();
        final List<RecentlyModifiedAsset> terms = new ArrayList<>();
        final List<RecentlyModifiedAsset> vocabularies = new ArrayList<>();
        final User author = Generator.generateUserWithId();
        for (int i = 0; i < count; i++) {
            RecentlyModifiedAsset rma = null;
            switch (i % 3) {
                case 0:
                    final Resource resource = Generator.generateResourceWithId();
                    rma = new RecentlyModifiedAsset(resource.getUri(), resource.getLabel(), new Date(), author.getUri(),
                            null,
                            cz.cvut.kbss.termit.util.Vocabulary.s_c_resource, Vocabulary.s_c_vytvoreni_entity);
                    resources.add(rma);
                    break;
                case 1:
                    final Term term = Generator.generateTermWithId();
                    rma = new RecentlyModifiedAsset(term.getUri(), term.getLabel().get(Constants.DEFAULT_LANGUAGE),
                            new Date(), author.getUri(), null,
                            SKOS.CONCEPT, Vocabulary.s_c_vytvoreni_entity);
                    terms.add(rma);
                    break;
                case 2:
                    final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
                    rma = new RecentlyModifiedAsset(vocabulary.getUri(), vocabulary.getLabel(), new Date(),
                            author.getUri(), null,
                            Vocabulary.s_c_slovnik, Vocabulary.s_c_vytvoreni_entity);
                    vocabularies.add(rma);
                    break;
            }
            rma.setModified(new Date(System.currentTimeMillis() - i * 1000L));
            rma.setEditor(author);
            assets.add(rma);
        }
        when(resourceService.findLastEdited(anyInt())).thenReturn(resources);
        when(resourceService.findLastEditedBy(any(User.class), anyInt())).thenReturn(resources);
        when(termService.findLastEdited(anyInt())).thenReturn(terms);
        when(termService.findLastEditedBy(any(User.class), anyInt())).thenReturn(terms);
        when(vocabularyService.findLastEdited(anyInt())).thenReturn(vocabularies);
        when(vocabularyService.findLastEditedBy(any(User.class), anyInt())).thenReturn(vocabularies);
        return assets;
    }

    @Test
    void findLastEditedReturnsAssetsSortedByDateCreatedDescending() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(6);
        allExpected.sort(Comparator.comparing(RecentlyModifiedAsset::getModified).reversed());
        final List<RecentlyModifiedAsset> result = sut.findLastEdited(10);
        assertEquals(allExpected, result);
    }

    @Test
    void findLastEditedReturnsSublistOfAssetsWhenCountIsLessThanTotalNumber() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(10);
        allExpected.sort(Comparator.comparing(RecentlyModifiedAsset::getModified).reversed());
        final int count = 6;
        final List<RecentlyModifiedAsset> result = sut.findLastEdited(count);
        assertEquals(allExpected.subList(0, count), result);
    }

    @Test
    void findLastEditedThrowsIllegalArgumentForCountLessThanZero() {
        assertThrows(IllegalArgumentException.class, () -> sut.findLastEdited(-1));
        verify(resourceService, never()).findLastEdited(anyInt());
        verify(termService, never()).findLastEdited(anyInt());
        verify(vocabularyService, never()).findLastEdited(anyInt());
    }

    @Test
    void findMyLastEditedGetsLastEditedByCurrentUser() {
        final List<RecentlyModifiedAsset> allExpected = generateRecentlyModifiedAssets(15);
        final UserAccount currentUser = Generator.generateUserAccount();
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        final int count = allExpected.size();
        final List<RecentlyModifiedAsset> result = sut.findMyLastEdited(count);
        assertEquals(count, result.size());
        assertTrue(allExpected.containsAll(result));
        verify(resourceService).findLastEditedBy(currentUser.toUser(), count);
        verify(termService).findLastEditedBy(currentUser.toUser(), count);
        verify(vocabularyService).findLastEditedBy(currentUser.toUser(), count);
    }

    private List<RecentlyCommentedAsset> generateRecentlyCommentedAssets(int count) {
        final List<RecentlyCommentedAsset> assets = new ArrayList<>();
        final User author = Generator.generateUserWithId();
        for (int i = 0; i < count; i++) {
            final Term term = Generator.generateTermWithId();
            Comment comment = Generator.generateComment(author, term);
            RecentlyCommentedAsset rca = new RecentlyCommentedAsset(term.getUri(), comment.getUri(), null, SKOS.CONCEPT);
            comment.setCreated(new Date(System.currentTimeMillis() - i * 1000));
            comment.setAuthor(author);
            comment.setAsset(term.getUri());
            rca.setLastComment(comment);
            assets.add(rca);
        }
        when(termService.findLastCommented(anyInt())).thenReturn(assets);
        when(termService.findMyLastCommented(any(User.class), anyInt())).thenReturn(assets);
        when(termService.findLastCommentedInReaction(any(User.class), anyInt())).thenReturn(assets);
        return assets;
    }

    @Test
    void findLastCommentedReturnsAssetsSortedByDateCommentCreatedDescending() {
        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets(6);
        allExpected.sort(Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        final List<RecentlyCommentedAsset> result = sut.findLastCommented(10);
        assertEquals(allExpected, result);
    }

    @Test
    void findMyLastCommentedReturnsAssetsSortedByDateCommentCreatedDescending() {
        final UserAccount currentUser = Generator.generateUserAccount();
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets(6);
        allExpected.sort(Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        final List<RecentlyCommentedAsset> result = sut.findMyLastCommented(10);
        assertEquals(allExpected, result);
    }

    @Test
    void findLastCommentedInReactionToMineReturnsAssetsSortedByDateCommentCreatedDescending() {
        final UserAccount currentUser = Generator.generateUserAccount();
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        final List<RecentlyCommentedAsset> allExpected = generateRecentlyCommentedAssets(6);
        allExpected.sort(Comparator.comparing((RecentlyCommentedAsset a) -> a.getLastComment().getCreated()).reversed());
        final List<RecentlyCommentedAsset> result = sut.findLastCommentedInReactionToMine(10);
        assertEquals(allExpected, result);
    }
}

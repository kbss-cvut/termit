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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.service.business.AssetService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/assets";

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AssetController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void getLastEditedRetrievesLastEditedAssetsFromService() throws Exception {
        final List<RecentlyModifiedAsset> assets = generateRecentlyModifiedAssetRecords();
        when(assetService.findLastEdited(anyInt())).thenReturn(assets);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/last-edited")).andExpect(status().isOk()).andReturn();
        final List<RecentlyModifiedAsset> result = readValue(mvcResult,
                new TypeReference<List<RecentlyModifiedAsset>>() {
                });
        assertEquals(assets, result);
        verify(assetService).findLastEdited(Integer.parseInt(AssetController.DEFAULT_LIMIT));
    }

    private static List<RecentlyModifiedAsset> generateRecentlyModifiedAssetRecords() {
        final User user = Generator.generateUserWithId();
        return IntStream.range(0, 5).mapToObj(i -> new RecentlyModifiedAsset(Generator.generateUri(), "Test " + i,
                new Date(), user.getUri(), null, Generator.randomBoolean() ?
                Vocabulary.s_c_slovnik :
                SKOS.CONCEPT, Vocabulary.s_c_vytvoreni_entity))
                .collect(Collectors.toList());
    }

    @Test
    void getLastEditedUsesQueryParameterToSpecifyMaximumNumberOfReturnedResults() throws Exception {
        when(assetService.findLastEdited(anyInt())).thenReturn(Collections.emptyList());
        final int limit = 10;
        mockMvc.perform(get(PATH + "/last-edited").param("limit", Integer.toString(limit))).andExpect(status().isOk());
        verify(assetService).findLastEdited(limit);
    }

    @Test
    void getLastEditedRetrievesCurrentUsersLastEditedWhenMineParameterIsSpecified() throws Exception {
        final List<RecentlyModifiedAsset> assets = generateRecentlyModifiedAssetRecords();
        when(assetService.findMyLastEdited(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/last-edited").param("forCurrentUserOnly", Boolean.TRUE.toString()))
                .andExpect(status().isOk());
        verify(assetService).findMyLastEdited(Integer.parseInt(AssetController.DEFAULT_LIMIT));
    }

    private static List<RecentlyCommentedAsset> generateRecentlyCommentedAssetRecords() {
        return IntStream.range(0, 5).mapToObj(i ->
            new RecentlyCommentedAsset( Generator.generateUri(), Generator.generateUri(), null, SKOS.CONCEPT ))
            .collect(Collectors.toList());
    }

    @Test
    void getLastCommentedRetrievesLastCommentedWithDefaultLimit() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommented(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/last-commented")).andExpect(status().isOk());
        verify(assetService).findLastCommented(Integer.parseInt(AssetController.DEFAULT_LIMIT));
    }

    @Test
    void getLastCommentedWithCustomLimitRetrievesLastCommentedWithCustomLimit() throws Exception {
        final int limit = Math.abs(Generator.randomInt());
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommented(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/last-commented")
            .param("limit", "" + limit))
            .andExpect(status().isOk());
        verify(assetService).findLastCommented(limit);
    }

    @Test
    void getMyLastCommentedRetrievesMyLastCommentedWithDefaultLimit() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findMyLastCommented(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/my-last-commented")).andExpect(status().isOk());
        verify(assetService).findMyLastCommented(Integer.parseInt(AssetController.DEFAULT_LIMIT));
    }

    @Test
    void getMyLastCommentedWithCustomLimitRetrievesMyLastCommentedWithCustomLimit() throws Exception {
        final int limit = Math.abs(Generator.randomInt());
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findMyLastCommented(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/my-last-commented")
            .param("limit", "" + limit))
            .andExpect(status().isOk());
        verify(assetService).findMyLastCommented(limit);
    }

    @Test
    void getLastCommentedInReactionToMineRetrievesLastCommentedInReactionToMineWithDefaultLimit() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommentedInReactionToMine(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/last-commented-in-reaction-to-mine")).andExpect(status().isOk());
        verify(assetService).findLastCommentedInReactionToMine(Integer.parseInt(AssetController.DEFAULT_LIMIT));
    }

    @Test
    void getLastCommentedInReactionToMineRetrievesLastCommentedInReactionToMineWithCustomLimit() throws Exception {
        final int limit = Math.abs(Generator.randomInt());
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommentedInReactionToMine(anyInt())).thenReturn(assets);
        mockMvc.perform(get(PATH + "/last-commented-in-reaction-to-mine")
            .param("limit", "" + limit))
            .andExpect(status().isOk());
        verify(assetService).findLastCommentedInReactionToMine(limit);
    }
}

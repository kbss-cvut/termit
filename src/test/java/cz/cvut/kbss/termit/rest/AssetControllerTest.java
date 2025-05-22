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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.service.business.AssetService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.rest.AssetController.DEFAULT_PAGE;
import static cz.cvut.kbss.termit.rest.AssetController.DEFAULT_PAGE_SIZE;
import static java.lang.Integer.parseInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        when(assetService.findLastEdited(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/last-edited")).andExpect(status().isOk()).andReturn();
        final List<RecentlyModifiedAsset> result = readValue(mvcResult,
                                                             new TypeReference<List<RecentlyModifiedAsset>>() {
                                                             });
        assertEquals(assets, result);
        verify(assetService).findLastEdited(PageRequest.of(parseInt(DEFAULT_PAGE), parseInt(DEFAULT_PAGE_SIZE)));
    }

    private static List<RecentlyModifiedAsset> generateRecentlyModifiedAssetRecords() {
        final User user = Generator.generateUserWithId();
        return IntStream.range(0, 5).mapToObj(i -> new RecentlyModifiedAsset(Generator.generateUri(), "Test " + i,
                                                                             Utils.timestamp(), user.getUri(), null,
                                                                             Generator.randomBoolean() ?
                                                                             Vocabulary.s_c_slovnik :
                                                                             SKOS.CONCEPT,
                                                                             Vocabulary.s_c_vytvoreni_entity))
                        .collect(Collectors.toList());
    }

    @Test
    void getLastEditedUsesQueryParameterToSpecifyPageOfReturnedResults() throws Exception {
        when(assetService.findLastEdited(any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        final int pageSize = 10;
        final int pageNo = Generator.randomInt(0, 5);
        mockMvc.perform(get(PATH + "/last-edited")
                                .param(Constants.QueryParams.PAGE_SIZE, Integer.toString(pageSize))
                                .param(Constants.QueryParams.PAGE, Integer.toString(pageNo)))
               .andExpect(status().isOk());
        verify(assetService).findLastEdited(PageRequest.of(pageNo, pageSize));
    }

    @Test
    void getLastEditedRetrievesCurrentUsersLastEditedWhenMineParameterIsSpecified() throws Exception {
        final List<RecentlyModifiedAsset> assets = generateRecentlyModifiedAssetRecords();
        when(assetService.findMyLastEdited(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/last-edited").param("forCurrentUserOnly", Boolean.TRUE.toString()))
               .andExpect(status().isOk());
        verify(assetService).findMyLastEdited(PageRequest.of(parseInt(DEFAULT_PAGE), parseInt(DEFAULT_PAGE_SIZE)));
    }

    private static List<RecentlyCommentedAsset> generateRecentlyCommentedAssetRecords() {
        return IntStream.range(0, 5).mapToObj(i -> new RecentlyCommentedAsset(Generator.generateUri(),
                                                                              "Term " + Generator.randomInt(0, 1000),
                                                                              Generator.generateUri(), null,
                                                                              Generator.generateUri(),
                                                                              SKOS.CONCEPT))
                        .collect(Collectors.toList());
    }

    @Test
    void getLastCommentedRetrievesFirstPageOfLastCommented() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommented(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/last-commented")).andExpect(status().isOk());
        verify(assetService).findLastCommented(
                PageRequest.of(parseInt(DEFAULT_PAGE), parseInt(DEFAULT_PAGE_SIZE)));
    }

    @Test
    void getLastCommentedWithCustomLimitRetrievesLastCommentedWithCustomLimit() throws Exception {
        final int pageSize = Math.abs(Generator.randomInt());
        final int pageNo = 2;
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommented(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/last-commented")
                                .param(Constants.QueryParams.PAGE_SIZE, "" + pageSize)
                                .param(Constants.QueryParams.PAGE, "" + pageNo))
               .andExpect(status().isOk());
        verify(assetService).findLastCommented(PageRequest.of(pageNo, pageSize));
    }

    @Test
    void getMyLastCommentedRetrievesFirstPageOfMyLastCommented() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findMyLastCommented(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/my-last-commented")).andExpect(status().isOk());
        verify(assetService).findMyLastCommented(PageRequest.of(parseInt(DEFAULT_PAGE), parseInt(DEFAULT_PAGE_SIZE)));
    }

    @Test
    void getMyLastCommentedWithCustomLimitRetrievesMyLastCommentedWithCustomLimit() throws Exception {
        final int pageSize = Math.abs(Generator.randomInt());
        final int pageNo = 2;
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findMyLastCommented(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/my-last-commented")
                                .param(Constants.QueryParams.PAGE_SIZE, "" + pageSize)
                                .param(Constants.QueryParams.PAGE, "" + pageNo))
               .andExpect(status().isOk());
        verify(assetService).findMyLastCommented(PageRequest.of(pageNo, pageSize));
    }

    @Test
    void getLastCommentedInReactionToMineRetrievesLastCommentedInReactionToMineWithDefaultLimit() throws Exception {
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommentedInReactionToMine(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/last-commented-in-reaction-to-mine")).andExpect(status().isOk());
        verify(assetService).findLastCommentedInReactionToMine(
                PageRequest.of(parseInt(DEFAULT_PAGE), parseInt(DEFAULT_PAGE_SIZE)));
    }

    @Test
    void getLastCommentedInReactionToMineRetrievesLastCommentedInReactionToMineWithCustomLimit() throws Exception {
        final int pageSize = Math.abs(Generator.randomInt());
        final int pageNo = Generator.randomInt(0, 5);
        final List<RecentlyCommentedAsset> assets = generateRecentlyCommentedAssetRecords();
        when(assetService.findLastCommentedInReactionToMine(any(Pageable.class))).thenReturn(new PageImpl<>(assets));
        mockMvc.perform(get(PATH + "/last-commented-in-reaction-to-mine")
                                .param(Constants.QueryParams.PAGE_SIZE, "" + pageSize)
                                .param(Constants.QueryParams.PAGE, "" + pageNo))
               .andExpect(status().isOk());
        verify(assetService).findLastCommentedInReactionToMine(PageRequest.of(pageNo, pageSize));
    }
}

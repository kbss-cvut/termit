/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.acl.AccessControlRecordDto;
import cz.cvut.kbss.termit.dto.acl.AccessHolderDto;
import cz.cvut.kbss.termit.dto.listing.DocumentDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.*;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;
import cz.cvut.kbss.termit.util.Configuration;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Mapper(componentModel = "spring")
public abstract class DtoMapper {

    @Autowired
    protected Configuration config;

    public abstract DocumentDto documentToDocumentDto(Document document);

    public abstract VocabularyDto vocabularyToVocabularyDto(Vocabulary vocabulary);

    public abstract AccessControlListDto accessControlListToDto(AccessControlList acl);

    public AccessControlRecordDto accessControlRecordToDto(AccessControlRecord<?> record) {
        final AccessControlRecordDto dto = new AccessControlRecordDto();
        dto.setUri(record.getUri());
        dto.setAccessLevel(record.getAccessLevel());
        dto.setHolder(accessHolderToDto(record.getHolder()));
        dto.setTypes(Collections.singleton(EntityToOwlClassMapper.getOwlClassForEntity(record.getClass())));
        return dto;
    }

    private <T extends AccessControlAgent> AccessHolderDto accessHolderToDto(T holder) {
        final AccessHolderDto dto = new AccessHolderDto();
        dto.setUri(holder.getUri());
        final String type = EntityToOwlClassMapper.getOwlClassForEntity(holder.getClass());
        dto.setTypes(new HashSet<>(Set.of(type)));
        switch (type) {
            case cz.cvut.kbss.termit.util.Vocabulary.s_c_uzivatel_termitu:
                final User user = (User) holder;
                assert user.getTypes() != null;
                user.getTypes().forEach(dto::addType);
                dto.setLabel(MultilingualString.create(user.getFullName(),
                                                       config.getPersistence().getLanguage()));
                break;
            case cz.cvut.kbss.termit.util.Vocabulary.s_c_Usergroup:
                dto.setLabel(MultilingualString.create(((UserGroup) holder).getLabel(),
                                                       config.getPersistence().getLanguage()));
                break;
            case cz.cvut.kbss.termit.util.Vocabulary.s_c_uzivatelska_role:
                dto.setLabel(((UserRole) holder).getLabel());
                break;
            default:
                throw new IllegalArgumentException("Unsupported access holder type " + holder);
        }
        return dto;
    }

    public RdfsResource assetToRdfsResource(Asset<?> asset) {
        final AssetToRdfsResourceMapper mapper = new AssetToRdfsResourceMapper(config.getPersistence().getLanguage());
        asset.accept(mapper);
        return mapper.getRdfsResource();
    }

    // Setter for test environment setup purposes
    public void setConfig(Configuration config) {
        this.config = config;
    }
}

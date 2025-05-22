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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

/**
 * Authorizes access to resources, files and documents in particular.
 */
@Service
public class ResourceAuthorizationService implements AssetAuthorizationService<Resource> {

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    public ResourceAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    @Override
    public boolean canRead(Resource asset) {
        return true;
    }

    @Override
    public boolean canModify(Resource asset) {
        return resolveVocabulary(asset).map(vocabularyAuthorizationService::canModify).orElse(true);
    }

    private Optional<Vocabulary> resolveVocabulary(Resource resource) {
        if (resource instanceof Document document) {
            final URI vocIri = document.getVocabulary();
            return vocIri != null ? Optional.of(new Vocabulary(vocIri)) : Optional.empty();
        } else if (resource instanceof File f) {
            return f.getDocument() != null ? getDocumentVocabulary(f.getDocument()) : Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Vocabulary> getDocumentVocabulary(Document doc) {
        final URI vocIri = doc.getVocabulary();
        return vocIri != null ? Optional.of(new Vocabulary(vocIri)) : Optional.empty();
    }

    @Override
    public boolean canRemove(Resource asset) {
        return resolveVocabulary(asset).map(vocabularyAuthorizationService::canRemoveFiles).orElse(true);
    }
}

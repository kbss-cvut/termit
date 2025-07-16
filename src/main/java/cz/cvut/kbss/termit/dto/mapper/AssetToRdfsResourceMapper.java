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
package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;
import cz.cvut.kbss.termit.util.Utils;

class AssetToRdfsResourceMapper implements AssetVisitor {

    private RdfsResource rdfsResource;

    private final String language;

    AssetToRdfsResourceMapper(String language) {
        this.language = language;
    }

    @Override
    public void visitTerm(AbstractTerm term) {
        this.rdfsResource = new RdfsResource(term.getUri(), term.getLabel(), term.getDefinition(), SKOS.CONCEPT);
        rdfsResource.getTypes().addAll(Utils.emptyIfNull(term.getTypes()));
    }

    @Override
    public void visitVocabulary(Vocabulary vocabulary) {
        this.rdfsResource = new RdfsResource(vocabulary.getUri(),
                                             vocabulary.getLabel(),
                                             vocabulary.getDescription(),
                                             cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik);
    }

    @Override
    public void visitResources(Resource resource) {
        this.rdfsResource = new RdfsResource(resource.getUri(),
                                             MultilingualString.create(resource.getLabel(), language),
                                             resource.getDescription() != null ?
                                             MultilingualString.create(resource.getDescription(), language) : null,
                                             EntityToOwlClassMapper.getOwlClassForEntity(resource.getClass()));
    }

    public RdfsResource getRdfsResource() {
        return rdfsResource;
    }
}

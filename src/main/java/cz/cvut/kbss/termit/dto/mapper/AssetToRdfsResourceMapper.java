package cz.cvut.kbss.termit.dto.mapper;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfsResource;
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
                                             MultilingualString.create(vocabulary.getLabel(), language),
                                             vocabulary.getDescription() != null ?
                                             MultilingualString.create(vocabulary.getDescription(), language) : null,
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

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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.util.Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS;

/**
 * Provides descriptors for working with repository contexts.
 */
@Component
public class DescriptorFactory {

    private final EntityManagerFactory emf;

    private final VocabularyContextMapper contextMapper;

    private final VocabularyDao vocabularyDao;

    @Autowired
    public DescriptorFactory(EntityManagerFactory emf, VocabularyContextMapper contextMapper,
                             @Lazy VocabularyDao vocabularyDao) {
        this.emf = emf;
        this.contextMapper = contextMapper;
        this.vocabularyDao = vocabularyDao;
    }

    /**
     * Creates a JOPA descriptor for the specified vocabulary.
     * <p>
     * The descriptor specifies that the instance context will correspond to the {@code vocabulary}'s IRI. It also
     * initializes other required attribute descriptors.
     *
     * @param vocabulary Vocabulary for which the descriptor should be created
     * @return Vocabulary descriptor
     */
    public Descriptor vocabularyDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return vocabularyDescriptor(vocabulary.getUri());
    }

    private EntityDescriptor assetDescriptor(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return new EntityDescriptor(contextMapper.getVocabularyContext(vocabularyUri));
    }

    public <T> FieldSpecification<? super T, ?> fieldSpec(Class<T> entityCls, String attribute) {
        Objects.requireNonNull(entityCls);
        Objects.requireNonNull(attribute);
        return emf.getMetamodel().entity(entityCls).getFieldSpecification(attribute);
    }

    /**
     * Creates a JOPA descriptor for a vocabulary with the specified identifier.
     * <p>
     * The descriptor specifies that the instance context will correspond to the given IRI. It also initializes other
     * required attribute descriptors.
     *
     * @param vocabularyUri Vocabulary identifier for which the descriptor should be created
     * @return Vocabulary descriptor
     */
    public Descriptor vocabularyDescriptor(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(fieldSpec(Vocabulary.class, "glossary"), glossaryDescriptor(vocabularyUri));
        descriptor.addAttributeDescriptor(fieldSpec(Vocabulary.class, "document"),
                                          documentDescriptor(vocabularyUri));
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link Document} related to the specified vocabulary (presumably a {@link
     * Vocabulary}).
     * <p>
     * This means that the context of the Document (and all its relevant attributes) is given by the specified
     * vocabulary's IRI.
     *
     * @param vocabulary Vocabulary on which the descriptor should be based
     * @return Document descriptor
     */
    public Descriptor documentDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return documentDescriptor(vocabulary.getUri());
    }

    /**
     * Creates a JOPA descriptor for a {@link Document} related to a vocabulary with the specified identifier
     * (presumably of a {@link Vocabulary}).
     * <p>
     * This means that the context of the Document (and all its relevant attributes) is given by the specified IRI.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return Document descriptor
     */
    public Descriptor documentDescriptor(URI vocabularyUri) {
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        final Descriptor fileDescriptor = fileDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(fieldSpec(Document.class, "files"), fileDescriptor);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.resource.File} related to the specified
     * vocabulary.
     * <p>
     * This means that the context of the File (and all its relevant attributes) is given by the specified vocabulary's
     * IRI.
     *
     * @param vocabulary Vocabulary identifier on which the descriptor should be based
     * @return File descriptor
     */
    public Descriptor fileDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return fileDescriptor(vocabulary.getUri());
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.resource.File} related to a vocabulary with the
     * specified identifier.
     * <p>
     * This means that the context of the File (and all its relevant attributes) is given by the specified IRI.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return File descriptor
     */
    public Descriptor fileDescriptor(URI vocabularyUri) {
        final Descriptor descriptor = assetDescriptor(vocabularyUri);
        final Descriptor docDescriptor = assetDescriptor(vocabularyUri);
        docDescriptor.addAttributeDescriptor(fieldSpec(Document.class, "files"), assetDescriptor(vocabularyUri));
        descriptor.addAttributeDescriptor(fieldSpec(File.class, "document"), docDescriptor);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.Glossary} related to the specified vocabulary.
     * <p>
     * This means that the context of the Glossary (and all its relevant attributes) is given by the specified
     * vocabulary's IRI.
     *
     * @param vocabulary Vocabulary on which the descriptor should be based
     * @return Glossary descriptor
     */
    public Descriptor glossaryDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return glossaryDescriptor(vocabulary.getUri());
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.Glossary} related to a vocabulary with the
     * specified identifier.
     * <p>
     * This means that the context of the Glossary (and all its relevant attributes) is given by the specified IRI.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return Glossary descriptor
     */
    public Descriptor glossaryDescriptor(URI vocabularyUri) {
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(fieldSpec(Glossary.class, "rootTerms"), termDescriptor(vocabularyUri));
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.Term} contained in the specified vocabulary.
     * <p>
     * This means that the context of the Term (and all its relevant attributes) is given by the specified vocabulary's
     * IRI.
     *
     * @param vocabulary Vocabulary on which the descriptor should be based
     * @return Term descriptor
     */
    public Descriptor termDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return termDescriptor(vocabulary.getUri());
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.Term} contained in a vocabulary with the
     * specified identifier.
     * <p>
     * This means that the context of the Term (and all its relevant attributes) is given by the specified vocabulary
     * IRI. SKOS attributes possibly referencing terms from different vocabularies are provided a descriptor based on
     * all vocabularies related to the specified one.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return Term descriptor
     */
    public Descriptor termDescriptor(URI vocabularyUri) {
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        final Descriptor interVocabularyRelationshipsDescriptor = resolveInterVocabularyTermRelationshipsDescriptor(
                vocabularyUri);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "externalParentTerms"),
                                          interVocabularyRelationshipsDescriptor);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "parentTerms"), descriptor);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "exactMatchTerms"),
                                          interVocabularyRelationshipsDescriptor);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "related"), descriptor);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "relatedMatch"),
                                          interVocabularyRelationshipsDescriptor);
        return descriptor;
    }

    private Descriptor resolveInterVocabularyTermRelationshipsDescriptor(URI vocabularyUri) {
        // TODO Cache somehow the related vocabularies
        final Vocabulary vocabulary = new Vocabulary(vocabularyUri);
        final Set<URI> related = new HashSet<>(
                vocabularyDao.getRelatedVocabularies(vocabulary, SKOS_CONCEPT_MATCH_RELATIONSHIPS));
        related.addAll(vocabularyDao.getTransitivelyImportedVocabularies(vocabulary));
        final Set<URI> relatedContexts = related.stream().map(contextMapper::getVocabularyContext)
                                                .collect(Collectors.toSet());
        return new EntityDescriptor(relatedContexts);
    }

    /**
     * Creates a JOPA descriptor for the specified term.
     * <p>
     * This takes the context from the term's vocabulary. Note that if parent terms are provided for the term, their
     * vocabularies are used as their contexts.
     *
     * @param term Term to create descriptor for
     * @return Term descriptor
     */
    public Descriptor termDescriptor(AbstractTerm term) {
        Objects.requireNonNull(term);
        assert term.getVocabulary() != null;
        return termDescriptor(term.getVocabulary());
    }

    /**
     * Creates a JOPA descriptor for saving the specified {@link cz.cvut.kbss.termit.model.Term}.
     * <p>
     * This method expects that the term has a vocabulary assigned. This vocabulary is used to determine the descriptor
     * context. If the term does not have a vocabulary assigned, use {@link #termDescriptorForSave(URI)}.
     * <p>
     * In addition, to allow for adding references to terms from previously unrelated vocabularies, attributes
     * representing SKOS mapping properties (broadMatch, exactMatch, relatedMatch) are assigned default context
     * descriptors. This is the main difference between the result of this method and {@link
     * #termDescriptor(AbstractTerm)}.
     *
     * @param term Term for which descriptor should be provided
     * @return Term descriptor
     */
    public Descriptor termDescriptorForSave(Term term) {
        Objects.requireNonNull(term);
        return termDescriptorForSave(term.getVocabulary());
    }

    /**
     * Creates a JOPA descriptor for saving a term to the context represented by the specified vocabulary identifier.
     * <p>
     * In addition, to allow for adding references to terms from previously unrelated vocabularies, attributes
     * representing SKOS mapping properties (broadMatch, exactMatch, relatedMatch) are assigned default context
     * descriptors. This is the main difference between the result of this method and {@link
     * #termDescriptor(AbstractTerm)}.
     *
     * @param vocabularyUri Vocabulary identifier used to determine the main target context
     * @return Term descriptor
     */
    public Descriptor termDescriptorForSave(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        descriptor.addAttributeContext(fieldSpec(Term.class, "externalParentTerms"), null);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "parentTerms"), descriptor);
        descriptor.addAttributeContext(fieldSpec(Term.class, "exactMatchTerms"), null);
        descriptor.addAttributeDescriptor(fieldSpec(Term.class, "related"), descriptor);
        descriptor.addAttributeContext(fieldSpec(Term.class, "relatedMatch"), null);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.dto.listing.TermDto} contained in any of the
     * vocabularies with the specified identifier.
     * <p>
     * This means that the context of the TermDto (and all its relevant attributes) is given by the vocabulary with the
     * specified IRI. ParentTerms attribute descriptor is the same as the one root descriptor, as these can be only from
     * the term's vocabulary.
     *
     * @param vocabularyUris Identifiers of vocabularies on which the descriptor should be based
     * @return TermDto descriptor
     */
    public Descriptor termDtoDescriptor(URI... vocabularyUris) {
        final Descriptor descriptor = new EntityDescriptor(Stream.of(vocabularyUris).map(
                contextMapper::getVocabularyContext).collect(
                Collectors.toSet()));
        descriptor.addAttributeDescriptor(fieldSpec(TermDto.class, "parentTerms"), descriptor);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.dto.listing.TermDto} contained in a vocabulary with
     * the specified identifier or any vocabulary imported by it.
     * <p>
     * This means that the context of the TermDto (and all its relevant attributes) is given by the vocabulary with the
     * specified IRI and all vocabularies it (transitively) imports. ParentTerms attribute descriptor is the same as the
     * one root descriptor, as these can be only from the term's vocabulary.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return TermDto descriptor
     */
    public Descriptor termDtoDescriptorWithImportedVocabularies(URI vocabularyUri) {
        final Set<URI> rootContexts = new HashSet<>();
        rootContexts.add(vocabularyUri);
        vocabularyDao.getTransitivelyImportedVocabularies(new Vocabulary(vocabularyUri)).stream().map(
                contextMapper::getVocabularyContext).forEach(rootContexts::add);
        final EntityDescriptor descriptor = new EntityDescriptor(rootContexts);
        descriptor.addAttributeDescriptor(fieldSpec(TermDto.class, "parentTerms"), descriptor);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for {@link cz.cvut.kbss.termit.dto.TermInfo} instances loaded from the specified
     * vocabularies.
     * <p>
     * This means that the context of the TermInfo (and all its relevant attributes) is given by the vocabulary with the
     * specified IRI.
     *
     * @param vocabularyUris Vocabulary identifiers
     * @return
     */
    public Descriptor termInfoDescriptor(URI... vocabularyUris) {
        return new EntityDescriptor(
                Stream.of(vocabularyUris).map(contextMapper::getVocabularyContext).collect(Collectors.toSet()));
    }
}

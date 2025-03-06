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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.model.Glossary_;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Term_;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.model.UserGroup_;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlList_;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord_;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.Document_;
import cz.cvut.kbss.termit.model.resource.File_;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides descriptors for working with repository contexts.
 */
@Component
public class DescriptorFactory {

    private final VocabularyContextMapper contextMapper;

    /**
     * Static descriptors are not dependent on particular instances.
     * <p>
     * They are based on the target entity class.
     */
    private final Map<Class<?>, Descriptor> staticDescriptors = new ConcurrentHashMap<>();

    @Autowired
    public DescriptorFactory(VocabularyContextMapper contextMapper) {
        this.contextMapper = contextMapper;
    }

    /**
     * Creates a JOPA descriptor for the specified vocabulary.
     * <p>
     * The descriptor specifies that the instance context will correspond to the {@code vocabulary}'s IRI. It also
     * initializes other required attribute descriptors.
     * <p>
     * Note that default context is used for asset author.
     *
     * @param vocabulary Vocabulary for which the descriptor should be created
     * @return Vocabulary descriptor
     */
    public Descriptor vocabularyDescriptor(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return vocabularyDescriptor(vocabulary.getUri());
    }

    /**
     * Creates a generic asset descriptor assuming the asset (and all its attribute values) are in the specified
     * vocabulary's context.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Asset descriptor
     */
    public EntityDescriptor assetDescriptor(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return new EntityDescriptor(contextMapper.getVocabularyContext(vocabularyUri));
    }

    /**
     * Creates a JOPA descriptor for a vocabulary with the specified identifier.
     * <p>
     * The descriptor specifies that the instance context will correspond to the given IRI. It also initializes other
     * required attribute descriptors.
     * <p>
     * Note that default context is used for asset author.
     *
     * @param vocabularyUri Vocabulary identifier for which the descriptor should be created
     * @return Vocabulary descriptor
     */
    public Descriptor vocabularyDescriptor(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(Vocabulary_.glossary, glossaryDescriptor(vocabularyUri));
        descriptor.addAttributeDescriptor(Vocabulary_.document, documentDescriptor(vocabularyUri));
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link Document} related to the specified vocabulary (presumably a
     * {@link Vocabulary}).
     * <p>
     * This means that the context of the Document (and all its relevant attributes) is given by the specified
     * vocabulary's IRI.
     * <p>
     * Note that default context is used for asset author.
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
     * <p>
     * Note that default context is used for asset author.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return Document descriptor
     */
    public Descriptor documentDescriptor(URI vocabularyUri) {
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        final Descriptor fileDescriptor = fileDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(Document_.files, fileDescriptor);
        return descriptor;
    }

    /**
     * Creates a JOPA descriptor for a {@link cz.cvut.kbss.termit.model.resource.File} related to the specified
     * vocabulary.
     * <p>
     * This means that the context of the File (and all its relevant attributes) is given by the specified vocabulary's
     * IRI.
     * <p>
     * Note that default context is used for asset author and last editor.
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
        docDescriptor.addAttributeDescriptor(Document_.files, assetDescriptor(vocabularyUri));
        descriptor.addAttributeDescriptor(File_.document, docDescriptor);
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
        descriptor.addAttributeDescriptor(Glossary_.rootTerms, termDescriptor(vocabularyUri));
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
     * IRI.
     *
     * @param vocabularyUri Vocabulary identifier on which the descriptor should be based
     * @return Term descriptor
     */
    public Descriptor termDescriptor(URI vocabularyUri) {
        final EntityDescriptor descriptor = assetDescriptor(vocabularyUri);
        final EntityDescriptor externalParentDescriptor = new EntityDescriptor();
        descriptor.addAttributeDescriptor(Term_.externalParentTerms, externalParentDescriptor);
        descriptor.addAttributeDescriptor(Term_.parentTerms, descriptor);
        final EntityDescriptor exactMatchTermsDescriptor = new EntityDescriptor();
        descriptor.addAttributeDescriptor(Term_.exactMatchTerms, exactMatchTermsDescriptor);
        final EntityDescriptor relatedDescriptor = new EntityDescriptor(vocabularyUri);
        descriptor.addAttributeDescriptor(Term_.related, relatedDescriptor);
        descriptor.addAttributeContext(Term_.relatedMatch, null);
        descriptor.addAttributeContext(Term_.definitionSource, null);
        return descriptor;
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
    public Descriptor termDescriptor(Term term) {
        Objects.requireNonNull(term);
        assert term.getVocabulary() != null;
        return termDescriptor(term.getVocabulary());
    }

    /**
     * Gets descriptor for storing {@link UserGroup} instances.
     * <p>
     * All instances are stored in the same repository context.
     *
     * @return Persistence descriptor
     */
    public Descriptor userGroupDescriptor() {
        return staticDescriptors.computeIfAbsent(UserGroup.class, (cls) -> {
            final EntityDescriptor descriptor = new EntityDescriptor(URI.create(StaticContexts.USER_GROUPS));
            descriptor.addAttributeContext(UserGroup_.members, null);
            return descriptor;
        });
    }

    /**
     * Gets a descriptor for storing {@link AccessControlList} instances.
     * <p>
     * All instances are stored in the same repository context.
     *
     * @return Persistence descriptor
     */
    public Descriptor accessControlListDescriptor() {
        return staticDescriptors.computeIfAbsent(AccessControlList.class, (cls) -> {
            final EntityDescriptor descriptor = new EntityDescriptor(URI.create(StaticContexts.ACCESS_CONTROL_LISTS));
            final EntityDescriptor recordsDesc = new EntityDescriptor(URI.create(StaticContexts.ACCESS_CONTROL_LISTS));
            recordsDesc.addAttributeContext(AccessControlRecord_.holder, null);
            descriptor.addAttributeDescriptor(AccessControlList_.records, recordsDesc);
            return descriptor;
        });
    }
}

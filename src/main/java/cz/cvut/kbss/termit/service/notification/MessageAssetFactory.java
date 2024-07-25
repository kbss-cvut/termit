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
package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.util.AssetVisitor;
import cz.cvut.kbss.termit.service.mail.ApplicationLinkBuilder;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MessageAssetFactory {

    private final DataRepositoryService dataService;

    private final ApplicationLinkBuilder linkBuilder;

    public MessageAssetFactory(DataRepositoryService dataService, ApplicationLinkBuilder linkBuilder) {
        this.dataService = dataService;
        this.linkBuilder = linkBuilder;
    }

    public MessageAsset create(Asset<?> asset) {
        final MessageLabelExtractor labelExtractor = new MessageLabelExtractor(dataService);
        asset.accept(labelExtractor);
        return new MessageAsset(labelExtractor.label, linkBuilder.linkTo(asset));
    }

    private static class MessageLabelExtractor implements AssetVisitor {
        private final DataRepositoryService dataService;

        String label;

        private MessageLabelExtractor(DataRepositoryService dataService) {
            this.dataService = dataService;
        }

        @Override
        public void visitTerm(AbstractTerm term) {
            this.label = term.getPrimaryLabel() + " (" + dataService.getLabel(term.getVocabulary(), null).orElse("") + ")";
        }

        @Override
        public void visitVocabulary(Vocabulary vocabulary) {
            this.label = vocabulary.getPrimaryLabel();
        }

        @Override
        public void visitResources(Resource resource) {
            this.label = resource.getPrimaryLabel();
        }
    }

    public static class MessageAsset implements Comparable<MessageAsset> {

        private final String label;
        private final String link;

        MessageAsset(String label, String link) {
            this.label = label;
            this.link = link;
        }

        public String getLabel() {
            return label;
        }

        public String getLink() {
            return link;
        }

        @Override
        public int compareTo(MessageAsset messageAsset) {
            return label.compareTo(messageAsset.label);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MessageAsset that = (MessageAsset) o;
            return label.equals(that.label) && link.equals(that.link);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, link);
        }
    }
}

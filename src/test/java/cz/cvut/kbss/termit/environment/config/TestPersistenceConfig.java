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
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.config.PersistenceConfig;
import cz.cvut.kbss.termit.environment.TestPersistenceFactory;
import cz.cvut.kbss.termit.service.validation.NoopRepositoryContextValidator;
import cz.cvut.kbss.termit.service.validation.RepositoryContextValidator;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import cz.cvut.kbss.termit.workspace.EditableVocabulariesHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.mockito.Mockito.spy;

@TestConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Import({TestPersistenceFactory.class, PersistenceConfig.class})
@ComponentScan(basePackages = "cz.cvut.kbss.termit.persistence")
@EnableTransactionManagement
public class TestPersistenceConfig {

    @Bean
    public EditableVocabularies editableVocabularies(Configuration config,
                                                     ObjectProvider<EditableVocabulariesHolder> editableVocabulariesHolder) {
        return new EditableVocabularies(config, editableVocabulariesHolder);
    }

    @Bean("spiedPublisher")
    @Primary
    public ApplicationEventPublisher eventPublisher(ApplicationEventPublisher eventPublisher) {
        return spy(eventPublisher);
    }

    @Bean
    public RepositoryContextValidator repositoryContextValidator() {
        return new NoopRepositoryContextValidator();
    }
}

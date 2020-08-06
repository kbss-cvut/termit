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
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.aspect.Aspects;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.workspace.WorkspaceMetadataCache;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Configuration
@PropertySource("classpath:config.properties")
@ComponentScan(basePackageClasses = {Aspects.class})
public class TestConfig {

    public static URI DEFAULT_WORKSPACE = Generator.generateUri();
    public static URI DEFAULT_VOCABULARY_CTX = Generator.generateUri();

    @Bean
    public cz.cvut.kbss.termit.util.Configuration configuration(Environment environment) {
        return new cz.cvut.kbss.termit.util.Configuration(environment);
    }

    @Bean(name = "workspaceStore")
    public WorkspaceStore workspaceStore() {
        final WorkspaceStore wsStore = spy(new WorkspaceStore(new MockHttpSession()));
        doReturn(DEFAULT_WORKSPACE).when(wsStore).getCurrentWorkspace();
        return wsStore;
    }

    @Bean
    public WorkspaceMetadataCache workspaceMetadataCache(WorkspaceStore workspaceStore) {
        final WorkspaceMetadataCache cache = spy(new WorkspaceMetadataCache(workspaceStore));
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        ws.setUri(DEFAULT_WORKSPACE);
        final WorkspaceMetadata wsMetadata = spy(new WorkspaceMetadata(ws));
        doReturn(wsMetadata).when(cache).getCurrentWorkspaceMetadata();
        doReturn(ws).when(cache).getCurrentWorkspace();
        final VocabularyInfo info = new VocabularyInfo(DEFAULT_VOCABULARY_CTX, DEFAULT_VOCABULARY_CTX,
                URI.create(DEFAULT_VOCABULARY_CTX.toString() +
                        Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION));
        doReturn(info).when(wsMetadata).getVocabularyInfo(any());
        return cache;
    }
}

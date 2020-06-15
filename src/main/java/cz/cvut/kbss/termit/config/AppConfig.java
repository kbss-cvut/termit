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
package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.aspect.Aspects;
import cz.cvut.kbss.termit.workspace.WorkspaceComponents;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.context.annotation.RequestScope;

import javax.servlet.http.HttpSession;

@Configuration
@EnableMBeanExport
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackageClasses = {Aspects.class, WorkspaceComponents.class})
@Import({PersistenceConfig.class, ServiceConfig.class, WebAppConfig.class})
@PropertySource("classpath:config.properties")
public class AppConfig {

    @Bean
    public cz.cvut.kbss.termit.util.Configuration configuration(Environment environment) {
        return new cz.cvut.kbss.termit.util.Configuration(environment);
    }

    @Bean(name = "workspaceStore")
    @RequestScope
    public WorkspaceStore workspaceStore(HttpSession session) {
        return new WorkspaceStore(session);
    }
}

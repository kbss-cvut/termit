package cz.cvut.kbss.termit.config;


import cz.cvut.kbss.termit.security.HierarchicalRoleBasedAuthorityMapper;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.cors.CorsConfigurationSource;

@Profile(Profiles.KEYCLOAK_AUTH)
@Configuration
@EnableWebSecurity
@KeycloakConfiguration
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityKeycloakConfig extends KeycloakWebSecurityConfigurerAdapter {

    private final cz.cvut.kbss.termit.util.Configuration config;

    @Autowired
    public SecurityKeycloakConfig(cz.cvut.kbss.termit.util.Configuration config) {
        this.config = config;
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(new HierarchicalRoleBasedAuthorityMapper());
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        http.authorizeRequests().antMatchers("/rest/query").permitAll()
            .and().cors().configurationSource(corsConfigurationSource())
            .and().csrf().disable()
            .authorizeRequests().antMatchers("/**").permitAll();

    }

    private CorsConfigurationSource corsConfigurationSource() {
        return SecurityConfig.createCorsConfiguration(config.getCors());
    }
}

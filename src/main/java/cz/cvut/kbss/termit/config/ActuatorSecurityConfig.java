package cz.cvut.kbss.termit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.core.userdetails.User.UserBuilder;

@Configuration
@EnableWebSecurity
@Order(1)
@Profile("!test")
public class ActuatorSecurityConfig {

    /**
     * Actuator access username
     *
     * @configurationdoc.default actuator
     */
    @Value("${termit.actuator.username:actuator}")
    private String actuatorUsername;

    /**
     * Actuator access password
     */
    @Value("${termit.actuator.password}")
    private String actuatorPassword;

    private final PasswordEncoder passwordEncoder;

    public ActuatorSecurityConfig(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Bean(name = "actuatorSecurityFilterChain")
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll()
                                               .requestMatchers("/actuator/**").authenticated())
            .userDetailsService(actuatorUserDetailsService())
            .httpBasic(withDefaults())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    public UserDetailsService actuatorUserDetailsService() {
        // The builder will ensure the passwords are encoded before saving in memory
        UserBuilder users = User.builder();
        UserDetails actuatorUser = users
                .username(actuatorUsername)
                .password(passwordEncoder.encode(actuatorPassword))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(actuatorUser);
    }
}

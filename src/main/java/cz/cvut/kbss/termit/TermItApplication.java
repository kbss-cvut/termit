package cz.cvut.kbss.termit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TermItApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(TermItApplication.class);
    }

    public static void main(String[] args) {
        // Ensures security context is propagated to additionally spun threads, e.g., used by @Async methods
        // Need to call it here before any of the Spring services depending on security context holder strategy are initialized
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SpringApplication.run(TermItApplication.class, args);
    }
}

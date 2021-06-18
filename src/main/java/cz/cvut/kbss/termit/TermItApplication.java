package cz.cvut.kbss.termit;

import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@EnableConfigurationProperties({Configuration.class, Configuration.Persistence.class, Configuration.Repository.class})
public class TermItApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(TermItApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(TermItApplication.class, args);
    }
}

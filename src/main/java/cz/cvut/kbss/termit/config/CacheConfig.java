package cz.cvut.kbss.termit.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!no-cache")
@Configuration
@EnableCaching
public class CacheConfig {
}

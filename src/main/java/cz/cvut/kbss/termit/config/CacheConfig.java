package cz.cvut.kbss.termit.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Enables cache unless the {@literal no-cache} Spring profile is active.
 *
 * Note that currently the caches are being used somewhat unusually - they cache whole lists of results instead of key-based
 * values. The main reason is that the caches were introduced to speed up retrieval of lists of assets (resources in particular)
 * and this is the simplest way to cache them.
 */
@Profile("!no-cache")
@Configuration
@EnableCaching
public class CacheConfig {
}

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
package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import cz.cvut.kbss.termit.service.cache.AccessControlListCacheKeyGenerator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
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

    @Bean
    public KeyGenerator accessControlListCacheKeyGenerator(AccessControlListDao aclDao) {
        return new AccessControlListCacheKeyGenerator(aclDao);
    }
}

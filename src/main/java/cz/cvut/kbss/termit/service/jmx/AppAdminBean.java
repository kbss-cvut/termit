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
package cz.cvut.kbss.termit.service.jmx;

import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyContentModified;
import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.Postman;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.stereotype.Component;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

@Component
@ManagedResource(description = "TermIt administration JMX bean.")
@Profile("!test")
public class AppAdminBean implements SelfNaming {

    private static final Logger LOG = LoggerFactory.getLogger(AppAdminBean.class);

    private final ApplicationEventPublisher eventPublisher;

    private final Postman postman;

    private final String beanName;

    @Autowired
    public AppAdminBean(ApplicationEventPublisher eventPublisher, Postman postman, Configuration config) {
        this.eventPublisher = eventPublisher;
        this.postman = postman;
        this.beanName = config.getJmxBeanName();
    }

    @CacheEvict(allEntries = true, cacheNames = {"vocabularies"})
    @ManagedOperation(description = "Invalidates the application caches.")
    public void invalidateCaches() {
        LOG.info("Invalidating application caches...");
        eventPublisher.publishEvent(new EvictCacheEvent(this));
        LOG.info("Refreshing last modified timestamps...");
        eventPublisher.publishEvent(new RefreshLastModifiedEvent(this));
        eventPublisher.publishEvent(new VocabularyContentModified(this));
    }

    @ManagedOperation(description = "Sends test email to the specified address.")
    public void sendTestEmail(String address) {
        final Message message = Message.to(address).subject("TermIt Test Email")
                                       .content("This is a test message from TermIt.").build();
        postman.sendMessage(message);
    }

    @Override
    public ObjectName getObjectName() throws MalformedObjectNameException {
        return new ObjectName("bean:name=" + beanName);
    }
}

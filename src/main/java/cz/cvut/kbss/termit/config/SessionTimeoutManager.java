package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

@WebListener
public class SessionTimeoutManager implements HttpSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTimeoutManager.class);

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        LOG.trace("Created session {}.", se.getSession().getId());
        se.getSession().setMaxInactiveInterval(SecurityConstants.SESSION_TIMEOUT);
    }
}

package cz.cvut.kbss.termit.metric;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that counts HTTP requests per user using a Micrometer counter.
 */
@Component
@Profile("!test")
public class UserRequestCounterInterceptor implements HandlerInterceptor {

    private final MeterRegistry meterRegistry;

    private final SecurityUtils securityUtils;

    public UserRequestCounterInterceptor(MeterRegistry meterRegistry, SecurityUtils securityUtils) {
        this.meterRegistry = meterRegistry;
        this.securityUtils = securityUtils;
    }

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                             @Nonnull Object handler) {
        if (securityUtils.isAuthenticated()) {
            final UserAccount account = securityUtils.getCurrentUser();
            Counter.builder("app.user.requests").description("HTTP requests per user")
                   .tag("user", account.getUsername()).register(meterRegistry).increment();
        }
        return true;
    }
}

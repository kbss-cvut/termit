package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.notification.PasswordChangeNotifier;
import cz.cvut.kbss.termit.service.repository.PasswordChangeRequestRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Supplier;

@Service
public class PasswordChangeService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordChangeService.class);

    public static final String INVALID_TOKEN_ERROR_MESSAGE_ID = "resetPassword.invalidToken";

    private final Configuration.Security securityConfig;
    private final PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService;
    private final UserRepositoryService userRepositoryService;
    private final PasswordChangeNotifier passwordChangeNotifier;

    public PasswordChangeService(PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService,
                                 Configuration configuration, UserRepositoryService userRepositoryService,
                                 PasswordChangeNotifier passwordChangeNotifier) {
        this.passwordChangeRequestRepositoryService = passwordChangeRequestRepositoryService;
        this.securityConfig = configuration.getSecurity();
        this.userRepositoryService = userRepositoryService;
        this.passwordChangeNotifier = passwordChangeNotifier;
    }

    @Transactional
    public void requestPasswordReset(String username) {
        // delete any existing request for the user
        passwordChangeRequestRepositoryService.findAllByUsername(username)
                                              .forEach(passwordChangeRequestRepositoryService::remove);

        UserAccount account = userRepositoryService.findByUsername(username)
                                                   .orElseThrow(() -> NotFoundException.create(UserAccount.class, username));
        PasswordChangeRequest request = passwordChangeRequestRepositoryService.create(account);
        passwordChangeNotifier.sendEmail(request);
    }

    private boolean isValid(PasswordChangeRequest request) {
        return request.getCreatedAt().plus(securityConfig.getPasswordChangeRequestValidity()).isAfter(Instant.now());
    }

    @Transactional
    public void changePassword(PasswordChangeDto passwordChangeDto) {
        Supplier<AuthorizationException> exception = () -> new InvalidPasswordChangeRequestException("Invalid or expired password change link", INVALID_TOKEN_ERROR_MESSAGE_ID);
        PasswordChangeRequest request = passwordChangeRequestRepositoryService.find(passwordChangeDto.getUri())
                                                                              .orElseThrow(exception);

        if (!request.getToken().equals(passwordChangeDto.getToken()) || !isValid(request)) {
            throw exception.get();
        }

        UserAccount account = userRepositoryService.find(request.getUserAccount().getUri())
                                                   .orElseThrow(exception);

        passwordChangeRequestRepositoryService.remove(request);

        account.setPassword(passwordChangeDto.getNewPassword());
        userRepositoryService.update(account);

        LOG.info("Password changed for user {}.", account.getUsername());
    }
}

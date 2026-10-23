package com.capstone.agenticrag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.agenticrag.user.Role;
import com.capstone.agenticrag.user.UserRepository;
import com.capstone.agenticrag.user.UserService;

/** Promotes existing accounts listed in ADMIN_EMAILS to the ADMIN role at startup. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final UserService userService;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, UserService userService, AppProperties props) {
        this.users = users;
        this.userService = userService;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.adminEmails() == null) {
            return;
        }
        props.adminEmails().stream().map(UserService::normalizeEmail).filter(e -> !e.isBlank())
                .forEach(email -> users.findByEmail(email).ifPresent(user -> {
                    if (user.getRole() != Role.ADMIN && userService.isConfiguredAdmin(email)) {
                        user.setRole(Role.ADMIN);
                        log.info("Promoted configured administrator account {}", user.getId());
                    }
                }));
    }
}

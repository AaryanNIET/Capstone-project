package com.capstone.agenticrag.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends every session of a user, e.g. after a password reset, account deletion or an admin disabling the account. */
@Component
public class SessionTerminator {

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    public SessionTerminator(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.sessions = sessions;
    }

    public void terminateAll(String email) {
        FindByIndexNameSessionRepository<? extends Session> repo = sessions.getIfAvailable();
        if (repo == null) {
            return;
        }
        repo.findByPrincipalName(email).keySet().forEach(repo::deleteById);
    }
}

package com.capstone.agenticrag.admin;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.capstone.agenticrag.common.Texts;

/** Records security-relevant and administrative actions. Never logs secrets or message content. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final TransactionTemplate tx;

    public AuditService(AuditLogRepository repository, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Inside a transaction the entry is written after it commits: the action only happened if it committed, and rows
     * the entry references (e.g. a newly registered user) are then visible to the audit insert.
     */
    public void record(UUID actorId, String actorEmail, String action, String targetType, String targetId, String details) {
        AuditLog entry = new AuditLog(actorId, actorEmail, action, targetType, targetId, Texts.truncate(details, 1000),
                currentIp());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(entry);
                }
            });
        } else {
            write(entry);
        }
    }

    private void write(AuditLog entry) {
        try {
            tx.executeWithoutResult(status -> repository.save(entry));
            log.info("audit action={} actor={} targetType={} targetId={}", entry.getAction(), entry.getActorId(),
                    entry.getTargetType(), entry.getTargetId());
        } catch (RuntimeException e) {
            log.error("Failed to write audit entry action={} targetId={}", entry.getAction(), entry.getTargetId(), e);
        }
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}

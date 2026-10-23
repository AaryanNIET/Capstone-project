package com.capstone.agenticrag.source;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSourceRepository extends JpaRepository<MessageSource, UUID> {

    List<MessageSource> findByMessageIdOrderByCitationAsc(UUID messageId);

    List<MessageSource> findByMessageIdInOrderByCitationAsc(Collection<UUID> messageIds);
}

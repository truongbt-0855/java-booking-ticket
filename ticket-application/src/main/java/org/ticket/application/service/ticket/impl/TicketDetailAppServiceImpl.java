package org.ticket.application.service.ticket.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.application.service.ticket.TicketDetailAppService;
import org.ticket.application.service.ticket.cache.TicketDetailCacheService;
import org.ticket.application.service.ticket.cache.TicketDetailCacheServiceV2;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.service.TicketDetailDomainService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailAppServiceImpl implements TicketDetailAppService {

    private final TicketDetailDomainService ticketDetailDomainService;

    // Cách cũ: CacheService tự handle lock + cache (vi phạm SRP)
    private final TicketDetailCacheService ticketDetailCacheService;

    // Cách mới: delegate sang CacheStampedeGuard (SRP, reusable)
    private final TicketDetailCacheServiceV2 ticketDetailCacheServiceV2;

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        log.info("Implement Application (cũ): {}", ticketId);
//        return ticketDetailDomainService.getTicketDetailById(ticketId);
//        return ticketDetailCacheService.getTicketDefaultCacheNormal(ticketId, System.currentTimeMillis());
        return ticketDetailCacheService.getTicketDefaultCacheVip(ticketId, System.currentTimeMillis());
    }

    @Override
    public TicketDetail getTicketDetailByIdV2(Long ticketId) {
        log.info("Implement Application (mới - CacheStampedeGuard): {}", ticketId);
        return ticketDetailCacheServiceV2.getTicketDetail(ticketId);
    }
}

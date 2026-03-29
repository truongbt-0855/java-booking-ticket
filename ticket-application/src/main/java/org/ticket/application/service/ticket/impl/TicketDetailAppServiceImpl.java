package org.ticket.application.service.ticket.impl;

import lombok.RequiredArgsConstructor;
import org.ticket.application.service.ticket.TicketDetailAppService;
import org.ticket.application.service.ticket.cache.TicketDetailCacheService;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.service.TicketDetailDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailAppServiceImpl implements TicketDetailAppService {

    private final TicketDetailDomainService ticketDetailDomainService;

    private final TicketDetailCacheService ticketDetailCacheService;

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        log.info("Implement Application : {}", ticketId);
//        return ticketDetailDomainService.getTicketDetailById(ticketId);
//        return ticketDetailCacheService.getTicketDefaultCacheNormal(ticketId, System.currentTimeMillis());
        return ticketDetailCacheService.getTicketDefaultCacheVip(ticketId, System.currentTimeMillis());
    }
}

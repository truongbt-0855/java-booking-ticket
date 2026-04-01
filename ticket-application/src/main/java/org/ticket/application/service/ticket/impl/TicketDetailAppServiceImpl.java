package org.ticket.application.service.ticket.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.application.mapper.TicketDetailMapper;
import org.ticket.application.model.TicketDetailDTO;
import org.ticket.application.model.cache.TicketDetailCache;
import org.ticket.application.service.ticket.TicketDetailAppService;
import org.ticket.application.service.ticket.cache.TicketDetailCacheService;
import org.ticket.application.service.ticket.cache.TicketDetailCacheServiceRefactor;
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
    private final TicketDetailCacheServiceRefactor ticketDetailCacheServiceRefactor;

    @Override
    public TicketDetailDTO getTicketDetailById(Long ticketId, Long version) {
        log.info("Implement Application : {}, {}: ", ticketId, version);
        TicketDetailCache ticketDetailCache = ticketDetailCacheServiceRefactor.getTicketDetail(ticketId, version);
        // mapper to DTO
        TicketDetailDTO ticketDetailDTO = TicketDetailMapper.mapperToTicketDetailDTO(ticketDetailCache.getTicketDetail());
        ticketDetailDTO.setVersion(ticketDetailCache.getVersion());
        return ticketDetailDTO;
    }

    @Override
    public boolean orderTicketByUser(Long ticketId) {
        return ticketDetailCacheServiceRefactor.orderTicketByUser(ticketId);
    }


    @Override
    public TicketDetail getTicketDetailByIdV2(Long ticketId) {
        log.info("Implement Application (mới - CacheStampedeGuard): {}", ticketId);
        return ticketDetailCacheServiceV2.getTicketDetail(ticketId);
    }
}

package org.ticket.domain.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.repository.TicketDetailRepository;
import org.ticket.domain.service.TicketDetailDomainService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailDomainServiceImpl implements TicketDetailDomainService {
    private final TicketDetailRepository ticketDetailRepository;

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        log.info("Implement Domain : {}", ticketId);
        return ticketDetailRepository.findById(ticketId).orElse(null);
    }
}


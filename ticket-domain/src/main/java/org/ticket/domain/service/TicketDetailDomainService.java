package org.ticket.domain.service;

import org.ticket.domain.model.entity.TicketDetail;

public interface TicketDetailDomainService {
    TicketDetail getTicketDetailById(Long ticketId);
}

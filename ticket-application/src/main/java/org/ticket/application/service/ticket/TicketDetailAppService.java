package org.ticket.application.service.ticket;

import org.ticket.domain.model.entity.TicketDetail;

public interface TicketDetailAppService {

    TicketDetail getTicketDetailById(Long ticketId); // should convert to TickDetailDTO by Application Module

    // Cách mới: dùng CacheStampedeGuard (SRP, reusable)
    TicketDetail getTicketDetailByIdV2(Long ticketId);
}

package org.ticket.application.service.ticket;

import org.ticket.application.model.TicketDetailDTO;
import org.ticket.domain.model.entity.TicketDetail;

public interface TicketDetailAppService {

    TicketDetailDTO getTicketDetailById(Long ticketId, Long version); // should convert to TickDetailDTO by Application Module

    boolean orderTicketByUser(Long ticketId);

    // Cách mới: dùng CacheStampedeGuard (SRP, reusable)
    TicketDetail getTicketDetailByIdV2(Long ticketId);
}

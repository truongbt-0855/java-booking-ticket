package org.ticket.application.mapper;

import org.ticket.application.model.TicketDetailDTO;
import org.ticket.domain.model.entity.Ticket;
import org.ticket.domain.model.entity.TicketDetail;
import org.springframework.beans.BeanUtils;

public class TicketDetailMapper {

    public static TicketDetailDTO mapperToTicketDetailDTO(TicketDetail ticketDetail) {
        if(ticketDetail == null) return null;

        TicketDetailDTO ticketDetailDTO = new TicketDetailDTO();
        BeanUtils.copyProperties(ticketDetail, ticketDetailDTO);

        return ticketDetailDTO;
    }
}

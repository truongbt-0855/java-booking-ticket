package org.ticket.application.model.cache;

import lombok.Data;
import org.ticket.domain.model.entity.TicketDetail;

@Data
public class TicketDetailCache {

    private Long version;
    private TicketDetail ticketDetail;

    public TicketDetailCache withClone(TicketDetail ticketDetail) {
        this.ticketDetail = ticketDetail;
        return this;
    }

    public TicketDetailCache withVersion(Long version) {
        this.version = version;
        return this;
    }

}

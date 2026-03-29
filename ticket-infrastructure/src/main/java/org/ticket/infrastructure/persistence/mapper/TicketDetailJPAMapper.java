package org.ticket.infrastructure.persistence.mapper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticket.domain.model.entity.TicketDetail;

public interface TicketDetailJPAMapper extends JpaRepository<TicketDetail, Long> {
}

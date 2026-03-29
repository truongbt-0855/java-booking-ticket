package org.ticket.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ticket.domain.model.entity.TicketDetail;
import org.ticket.domain.repository.TicketDetailRepository;
import org.ticket.infrastructure.persistence.mapper.TicketDetailJPAMapper;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketDetailInfraRepositoryImpl implements TicketDetailRepository {
    private final TicketDetailJPAMapper ticketDetailJPAMapper;

    @Override
    public Optional<TicketDetail> findById(Long id) {
        log.info("Implement Infrastructure : {}", id);
        return ticketDetailJPAMapper.findById(id);
    }
}

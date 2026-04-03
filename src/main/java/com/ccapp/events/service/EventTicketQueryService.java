package com.ccapp.events.service;

import com.ccapp.events.dto.MyEventTicketResponse;
import com.ccapp.events.repository.EventTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventTicketQueryService {

    private final EventTicketRepository eventTicketRepository;
    private final EventAccessService eventAccessService;
    private final EventDtoAssembler eventDtoAssembler;

    @Transactional(readOnly = true)
    public List<MyEventTicketResponse> listCurrentUserTickets() {
        return eventTicketRepository.findByBuyerAccountHolderIdOrderByCreatedAtDesc(eventAccessService.currentAccountHolderId()).stream()
                .map(eventDtoAssembler::toMyTicketResponse)
                .toList();
    }
}

package com.ccapp.common.events;

public interface DomainEventPublisher {
    void publish(DomainEvent event);
}

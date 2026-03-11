package com.ccapp.common.events;

public interface DomainEventListener<E extends DomainEvent> {
    void on(E event);
}

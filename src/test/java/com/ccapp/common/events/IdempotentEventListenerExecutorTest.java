package com.ccapp.common.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentEventListenerExecutorTest {

    @Mock
    private EventListenerCheckpointRepository checkpointRepository;

    @InjectMocks
    private IdempotentEventListenerExecutor executor;

    @Test
    void execute_shouldRunHandler_whenCheckpointInserted() {
        when(checkpointRepository.insertIfAbsent(any(UUID.class), anyString(), any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(1);
        AtomicInteger counter = new AtomicInteger();

        executor.execute("listener-1", new TestDomainEvent(UUID.randomUUID(), OffsetDateTime.now()), counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    void execute_shouldSkipHandler_whenCheckpointAlreadyExists() {
        when(checkpointRepository.insertIfAbsent(any(UUID.class), anyString(), any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(0);
        AtomicInteger counter = new AtomicInteger();

        executor.execute("listener-1", new TestDomainEvent(UUID.randomUUID(), OffsetDateTime.now()), counter::incrementAndGet);

        assertEquals(0, counter.get());
    }

    private record TestDomainEvent(UUID eventId, OffsetDateTime occurredAt) implements DomainEvent {
    }
}

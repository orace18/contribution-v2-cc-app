package com.ccapp.mlm.graph;

import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlmGraphServiceTest {

    @Mock
    private MlmGraphNeo4jGateway graphGateway;
    @Mock
    private MlmRelationshipRepository mlmRelationshipRepository;
    @Mock
    private MlmVolumeLedgerRepository mlmVolumeLedgerRepository;

    @InjectMocks
    private MlmGraphService mlmGraphService;

    @Test
    void getUpline_shouldReturnDeepChainFromNeo4j() {
        UUID userId = UUID.randomUUID();
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        UUID l3 = UUID.randomUUID();
        UUID l4 = UUID.randomUUID();
        UUID l5 = UUID.randomUUID();

        when(graphGateway.isAvailable()).thenReturn(true);
        when(graphGateway.read(anyString(), anyMap())).thenReturn(List.of(
                Map.of("memberId", l1.toString(), "level", 1),
                Map.of("memberId", l2.toString(), "level", 2),
                Map.of("memberId", l3.toString(), "level", 3),
                Map.of("memberId", l4.toString(), "level", 4),
                Map.of("memberId", l5.toString(), "level", 5)
        ));

        List<MlmGraphService.GraphMember> upline = mlmGraphService.getUpline(userId, 5);

        assertEquals(5, upline.size());
        assertEquals(l1, upline.get(0).memberId());
        assertEquals(1, upline.get(0).level());
        assertEquals(l5, upline.get(4).memberId());
        assertEquals(5, upline.get(4).level());
    }
}


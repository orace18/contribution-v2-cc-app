package com.ccapp.mlm.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmVolumeLedger;
import com.ccapp.observability.PlatformMonitoringHooks;
import com.ccapp.mlm.repository.MlmRelationshipRepository;
import com.ccapp.mlm.repository.MlmVolumeLedgerRepository;
import com.ccapp.shop.event.ShopOrderDeliveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlmVolumeLedgerService {

    private final MlmRelationshipRepository mlmRelationshipRepository;
    private final MlmVolumeLedgerRepository mlmVolumeLedgerRepository;

    @Value("${mlm.query.max-safe-depth:64}")
    private int maxSafeDepth;

    @Transactional
    public int recordForDeliveredOrder(ShopOrderDeliveredEvent event) {
        if (event == null || event.mlmVolume() == null || event.mlmVolume().signum() <= 0) {
            return 0;
        }
        int entries = recordUplineVolume(
                event.buyerId(),
                event.orderId(),
                event.mlmVolume(),
                event.occurredAt()
        );
        PlatformMonitoringHooks.onLedgerVolumeEntriesWritten(entries);
        log.info("mlm_volume_ledger_recorded orderId={} buyerId={} mlmVolume={} entries={}",
                event.orderId(), event.buyerId(), event.mlmVolume(), entries);
        return entries;
    }

    @Transactional
    public int recordUplineVolume(UUID sourceMemberId,
                                  UUID sourceOrderId,
                                  BigDecimal volumeCv,
                                  OffsetDateTime occurredAt) {
        return recordUplineVolume(sourceMemberId, sourceOrderId, volumeCv, occurredAt, false);
    }

    @Transactional
    public int rebuildForDeliveredOrder(UUID sourceMemberId,
                                        UUID sourceOrderId,
                                        BigDecimal volumeCv,
                                        OffsetDateTime occurredAt) {
        mlmVolumeLedgerRepository.deleteBySourceOrderId(sourceOrderId);
        return recordUplineVolume(sourceMemberId, sourceOrderId, volumeCv, occurredAt, true);
    }

    private int recordUplineVolume(UUID sourceMemberId,
                                   UUID sourceOrderId,
                                   BigDecimal volumeCv,
                                   OffsetDateTime occurredAt,
                                   boolean rebuilding) {
        if (volumeCv == null || volumeCv.signum() <= 0) {
            return 0;
        }

        List<MlmRelationship> ancestors = mlmRelationshipRepository.findAncestors(sourceMemberId);
        if (ancestors.isEmpty()) {
            throw new BusinessException("MLM tree not initialized for source member");
        }
        if (ancestors.size() > maxSafeDepth + 1) {
            throw new BusinessException("MLM hierarchy depth exceeds configured safe threshold");
        }

        int weekId = toWeekId(occurredAt != null ? occurredAt : OffsetDateTime.now());
        Set<UUID> existingForOrder = mlmVolumeLedgerRepository.findAccountHolderIdsBySourceOrderId(sourceOrderId);

        List<MlmVolumeLedger> toInsert = new ArrayList<>();
        for (MlmRelationship ancestor : ancestors) {
            if (ancestor.getLevel() == null || ancestor.getLevel() <= 0) {
                continue;
            }
            UUID uplineId = ancestor.getAncestorId();
            boolean exists = existingForOrder.contains(uplineId)
                    || mlmVolumeLedgerRepository.existsBySourceOrderIdAndAccountHolderId(sourceOrderId, uplineId);
            if (exists && !rebuilding) {
                continue;
            }
            int legPosition = resolveLegPosition(uplineId, sourceMemberId);
            toInsert.add(MlmVolumeLedger.builder()
                    .accountHolderId(uplineId)
                    .sourceMemberId(sourceMemberId)
                    .sourceOrderId(sourceOrderId)
                    .legPosition(legPosition)
                    .volumeCv(volumeCv)
                    .weekId(weekId)
                    .build());
        }

        if (toInsert.isEmpty()) {
            return 0;
        }
        try {
            mlmVolumeLedgerRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate MLM volume ledger rows skipped for sourceOrderId={}", sourceOrderId);
            return (int) toInsert.stream()
                    .filter(entry -> mlmVolumeLedgerRepository.existsBySourceOrderIdAndAccountHolderId(
                            entry.getSourceOrderId(),
                            entry.getAccountHolderId()))
                    .count();
        }
        log.info("mlm_volume_ledger_upserted sourceOrderId={} sourceMemberId={} weekId={} entries={} volumeCv={}",
                sourceOrderId, sourceMemberId, weekId, toInsert.size(), volumeCv);
        return toInsert.size();
    }

    private int resolveLegPosition(UUID ancestorId, UUID sourceMemberId) {
        List<UUID> directChildren = mlmRelationshipRepository.findDirectChildrenOrdered(ancestorId);
        for (int i = 0; i < directChildren.size(); i++) {
            UUID childId = directChildren.get(i);
            if (childId.equals(sourceMemberId)
                    || mlmRelationshipRepository.existsByAncestorIdAndDescendantId(childId, sourceMemberId)) {
                return i + 1;
            }
        }
        throw new BusinessException("Unable to resolve MLM leg position for source member");
    }

    private int toWeekId(OffsetDateTime timestamp) {
        int weekYear = timestamp.get(IsoFields.WEEK_BASED_YEAR);
        int week = timestamp.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return (weekYear * 100) + week;
    }
}

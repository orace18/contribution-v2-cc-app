package com.ccapp.observability;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.time.Duration;

public final class PlatformMonitoringHooks {

    private PlatformMonitoringHooks() {
    }

    public static void onCommissionCalculated(String commissionType, BigDecimal amount) {
        Metrics.counter("ccapp.commissions.calculated.total", "type", safeTag(commissionType)).increment();
        if (amount != null) {
            Metrics.summary("ccapp.commissions.calculated.amount", "type", safeTag(commissionType))
                    .record(amount.doubleValue());
        }
    }

    public static void onWeeklyBonusJob(String status, int membersProcessed, int commissionsCreated, Duration duration) {
        Metrics.counter("ccapp.weekly_bonus.jobs.total", "status", safeTag(status)).increment();
        Metrics.summary("ccapp.weekly_bonus.members.processed").record(membersProcessed);
        Metrics.summary("ccapp.weekly_bonus.commissions.created").record(commissionsCreated);
        if (duration != null) {
            Timer.builder("ccapp.weekly_bonus.job.duration")
                    .tag("status", safeTag(status))
                    .register(Metrics.globalRegistry)
                    .record(duration);
        }
    }

    public static void onPayoutBatchProcessed(String status, int paidItems, int failedItems, Duration duration) {
        Metrics.counter("ccapp.commission_payout.batches.total", "status", safeTag(status)).increment();
        Metrics.summary("ccapp.commission_payout.items.paid").record(paidItems);
        Metrics.summary("ccapp.commission_payout.items.failed").record(failedItems);
        if (duration != null) {
            Timer.builder("ccapp.commission_payout.batch.duration")
                    .tag("status", safeTag(status))
                    .register(Metrics.globalRegistry)
                    .record(duration);
        }
    }

    public static void onMarketplaceOrderCheckout(int itemCount, BigDecimal totalAmount) {
        Metrics.counter("ccapp.marketplace.checkout.total").increment();
        Metrics.summary("ccapp.marketplace.checkout.items").record(itemCount);
        if (totalAmount != null) {
            Metrics.summary("ccapp.marketplace.checkout.amount").record(totalAmount.doubleValue());
        }
    }

    public static void onMarketplaceOrderDelivered(BigDecimal mlmVolume) {
        Metrics.counter("ccapp.marketplace.order.delivered.total").increment();
        if (mlmVolume != null) {
            Metrics.summary("ccapp.marketplace.order.delivered.mlm_volume").record(mlmVolume.doubleValue());
        }
    }

    public static void onLedgerVolumeEntriesWritten(int entries) {
        Metrics.counter("ccapp.mlm.volume_ledger.writes.total").increment();
        Metrics.summary("ccapp.mlm.volume_ledger.entries").record(entries);
    }

    public static void onMlmPlacementCreated() {
        Metrics.counter("ccapp.mlm.placements.created.total").increment();
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}

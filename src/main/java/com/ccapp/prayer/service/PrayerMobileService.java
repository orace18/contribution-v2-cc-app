package com.ccapp.prayer.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.prayer.dto.CreatePrayerCommentRequest;
import com.ccapp.prayer.dto.CreatePrayerRequest;
import com.ccapp.prayer.dto.DailyPrayerCompletionResponse;
import com.ccapp.prayer.dto.DailyPrayerResponse;
import com.ccapp.prayer.dto.PrayerCommentResponse;
import com.ccapp.prayer.dto.PrayerDashboardResponse;
import com.ccapp.prayer.dto.PrayerInteractionResponse;
import com.ccapp.prayer.dto.PrayerPostResponse;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PrayerMobileService {

    private static final String NOT_IMPLEMENTED_MESSAGE = "PRAYER_PHASE1_NOT_IMPLEMENTED";

    public PageResponse<PrayerPostResponse> getPrayerFeed(UUID actorUserId,
                                                          UUID accountHolderId,
                                                          int page,
                                                          int size,
                                                          boolean includeMinePrivate) {
        throw notImplemented();
    }

    public PrayerPostResponse createPrayerRequest(UUID actorUserId,
                                                  UUID accountHolderId,
                                                  CreatePrayerRequest request) {
        throw notImplemented();
    }

    public PrayerPostResponse getPrayerPost(UUID actorUserId, UUID accountHolderId, UUID postId) {
        throw notImplemented();
    }

    public PrayerInteractionResponse recordAmen(UUID actorUserId, UUID accountHolderId, UUID postId) {
        throw notImplemented();
    }

    public PrayerInteractionResponse recordSupport(UUID actorUserId, UUID accountHolderId, UUID postId) {
        throw notImplemented();
    }

    public PageResponse<PrayerCommentResponse> listPrayerComments(UUID actorUserId,
                                                                  UUID accountHolderId,
                                                                  UUID postId,
                                                                  int page,
                                                                  int size) {
        throw notImplemented();
    }

    public PrayerCommentResponse createPrayerComment(UUID actorUserId,
                                                     UUID accountHolderId,
                                                     UUID postId,
                                                     CreatePrayerCommentRequest request) {
        throw notImplemented();
    }

    public DailyPrayerResponse getCurrentDailyPrayer(UUID actorUserId, UUID accountHolderId, String timezone) {
        throw notImplemented();
    }

    public DailyPrayerCompletionResponse completeCurrentDailyPrayer(UUID actorUserId,
                                                                    UUID accountHolderId,
                                                                    String timezone) {
        throw notImplemented();
    }

    public PrayerDashboardResponse getPrayerDashboard(UUID actorUserId, UUID accountHolderId, String timezone) {
        throw notImplemented();
    }

    private PrayerPhase1NotImplementedException notImplemented() {
        return new PrayerPhase1NotImplementedException(NOT_IMPLEMENTED_MESSAGE);
    }
}

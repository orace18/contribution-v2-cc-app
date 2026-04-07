package com.ccapp.prayer.service;

import com.ccapp.common.api.PageResponse;
import com.ccapp.prayer.domain.enums.PrayerOfficialVisibilityScope;
import com.ccapp.prayer.domain.enums.PrayerPostLifecycleStatus;
import com.ccapp.prayer.dto.AdminCreatePrayerPostRequest;
import com.ccapp.prayer.dto.AdminPrayerPostResponse;
import com.ccapp.prayer.dto.AdminUpdatePrayerPostRequest;
import com.ccapp.prayer.exception.PrayerPhase1NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PrayerAdminPostService {

    private static final String NOT_IMPLEMENTED_MESSAGE = "PRAYER_PHASE1_NOT_IMPLEMENTED";

    public PageResponse<AdminPrayerPostResponse> listPosts(int page,
                                                           int size,
                                                           PrayerPostLifecycleStatus status,
                                                           PrayerOfficialVisibilityScope visibility) {
        throw notImplemented();
    }

    public AdminPrayerPostResponse createPost(UUID adminUserId, AdminCreatePrayerPostRequest request) {
        throw notImplemented();
    }

    public AdminPrayerPostResponse getPost(UUID postId) {
        throw notImplemented();
    }

    public AdminPrayerPostResponse updatePost(UUID adminUserId, UUID postId, AdminUpdatePrayerPostRequest request) {
        throw notImplemented();
    }

    public AdminPrayerPostResponse publishPost(UUID adminUserId, UUID postId) {
        throw notImplemented();
    }

    public AdminPrayerPostResponse archivePost(UUID adminUserId, UUID postId) {
        throw notImplemented();
    }

    private PrayerPhase1NotImplementedException notImplemented() {
        return new PrayerPhase1NotImplementedException(NOT_IMPLEMENTED_MESSAGE);
    }
}

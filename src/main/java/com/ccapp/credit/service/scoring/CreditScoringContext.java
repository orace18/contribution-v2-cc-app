package com.ccapp.credit.service.scoring;

import com.ccapp.circlesocial.dto.CircleScoreResponse;
import com.ccapp.progress.dto.UserProgressResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class CreditScoringContext {

    private final UUID accountHolderId;
    private final UUID circleId;
    private final Supplier<CircleScoreResponse> circleScoreSupplier;
    private final Supplier<UserProgressResponse> progressSupplier;

    private CircleScoreResponse cachedCircleScore;
    private UserProgressResponse cachedProgress;

    public CreditScoringContext(UUID accountHolderId,
                                UUID circleId,
                                Supplier<CircleScoreResponse> circleScoreSupplier,
                                Supplier<UserProgressResponse> progressSupplier) {
        this.accountHolderId = accountHolderId;
        this.circleId = circleId;
        this.circleScoreSupplier = circleScoreSupplier;
        this.progressSupplier = progressSupplier;
    }

    public UUID accountHolderId() {
        return accountHolderId;
    }

    public UUID circleId() {
        return circleId;
    }

    public Optional<CircleScoreResponse> circleScore() {
        if (circleId == null) {
            return Optional.empty();
        }
        if (cachedCircleScore == null) {
            cachedCircleScore = circleScoreSupplier.get();
        }
        return Optional.ofNullable(cachedCircleScore);
    }

    public Optional<UserProgressResponse> userProgress() {
        if (cachedProgress == null) {
            cachedProgress = progressSupplier.get();
        }
        return Optional.ofNullable(cachedProgress);
    }
}

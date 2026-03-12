package com.ccapp.identity.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.identity.domain.enums.BusinessExperienceLevel;
import com.ccapp.identity.domain.enums.OnboardingStep;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id")
})
public class UserProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_profiles_user"))
    private User user;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "profile_photo_media_id")
    private UUID profilePhotoMediaId;

    @Column(name = "activity_sector_code", length = 64)
    private String activitySectorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_experience", length = 32)
    private BusinessExperienceLevel businessExperience;

    @Column(name = "monthly_income_bracket_code", length = 64)
    private String monthlyIncomeBracketCode;

    @Column(name = "project_description", length = 1000)
    private String projectDescription;

    @Column(name = "available_capital", precision = 19, scale = 4)
    private BigDecimal availableCapital;

    @Column(name = "solidarity_guarantee_accepted", nullable = false)
    private boolean solidarityGuaranteeAccepted;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false, length = 16)
    private OnboardingStep onboardingStep;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (onboardingStep == null) {
            onboardingStep = OnboardingStep.STEP_1;
        }
    }
}

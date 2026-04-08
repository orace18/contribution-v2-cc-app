package com.ccapp.mobile.home.service;

import com.ccapp.circles.dto.MyCircleResponse;
import com.ccapp.circles.service.CircleService;
import com.ccapp.circlesocial.dto.CircleActivityResponse;
import com.ccapp.circlesocial.service.CircleActivityService;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.credit.dto.CreditEligibilityResponse;
import com.ccapp.credit.service.CreditScoringService;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.repository.UserProfileRepository;
import com.ccapp.mobile.home.dto.MobileHomeDashboardResponse;
import com.ccapp.mobile.onboarding.domain.entity.MobilePlanSelection;
import com.ccapp.mobile.onboarding.domain.enums.MobilePlanSelectionStatus;
import com.ccapp.mobile.onboarding.repository.MobilePlanSelectionRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskResponse;
import com.ccapp.progress.repository.UserTaskRepository;
import com.ccapp.progress.service.TaskEngineService;
import com.ccapp.prayer.dto.PrayerHomeSuggestionResponse;
import com.ccapp.prayer.service.PrayerSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeFacadeService {

    private static final int TODAY_TASK_LIMIT = 3;
    private static final int CIRCLE_ACTIVITY_LIMIT = 3;
    private static final List<MobileHomeDashboardResponse.QuickAction> DEFAULT_QUICK_ACTIONS = List.of(
            new MobileHomeDashboardResponse.QuickAction("CREATE_CIRCLE", "Creer un cercle"),
            new MobileHomeDashboardResponse.QuickAction("JOIN_CIRCLE", "Rejoindre un cercle"),
            new MobileHomeDashboardResponse.QuickAction("APPLY_FINANCING", "Financement")
    );

    private final AccessControlService accessControlService;
    private final AccountHolderService accountHolderService;
    private final AccountHolderRepository accountHolderRepository;
    private final TaskEngineService taskEngineService;
    private final UserTaskRepository userTaskRepository;
    private final MembershipService membershipService;
    private final CircleService circleService;
    private final CircleActivityService circleActivityService;
    private final AppUserRepository appUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final CreditScoringService creditScoringService;
    private final MobilePlanSelectionRepository mobilePlanSelectionRepository;
    private final PrayerSuggestionService prayerSuggestionService;

    @Transactional
    public MobileHomeDashboardResponse getHome(UUID actorUserId, UUID accountHolderId) {
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, PermissionLevel.VIEW);

        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        UserProfile profile = resolveProfile(accountHolderId);
        Optional<Membership> activeMembership = membershipService.findLatestMembership(accountHolderId)
                .filter(this::isActiveMembership);
        Optional<MobilePlanSelection> planSelection = mobilePlanSelectionRepository.findByAccountHolderId(accountHolderId);
        List<TaskResponse> allTasks = taskEngineService.getAllUserTasks(accountHolderId);
        MyCircleResponse myCircle = findMyCircle(accountHolderId);
        CreditEligibilityResponse eligibility = creditScoringService.getEligibility(actorUserId, accountHolderId);
        MobileHomeDashboardResponse.FinancingSection financing = buildFinancing(profile, eligibility);
        PrayerHomeSuggestionResponse prayerSuggestion = prayerSuggestionService.getHomeSuggestion(
                accountHolder,
                myCircle != null,
                activeMembership.isPresent(),
                financing.remainingAmount().signum() > 0
        );

        return new MobileHomeDashboardResponse(
                buildUser(actorUserId, accountHolder, profile, activeMembership.orElse(null)),
                buildEngagement(accountHolderId, allTasks),
                buildNextAction(accountHolder, profile, myCircle, activeMembership.isPresent(), planSelection.orElse(null), allTasks),
                buildCircle(myCircle),
                DEFAULT_QUICK_ACTIONS,
                financing,
                prayerSuggestion
        );
    }

    private MobileHomeDashboardResponse.UserSection buildUser(UUID actorUserId,
                                                              AccountHolder accountHolder,
                                                              UserProfile profile,
                                                              Membership activeMembership) {
        String membershipType = activeMembership == null
                ? "NONE"
                : activeMembership.getPlanGrade() != null
                ? activeMembership.getPlanGrade().name()
                : activeMembership.getPlanCode();
        boolean delegatedAccess = isDelegatedAccess(actorUserId, accountHolder.getId());
        return new MobileHomeDashboardResponse.UserSection(
                accountHolder.getFirstName(),
                profile != null && profile.getProfilePhotoMediaId() != null ? profile.getProfilePhotoMediaId().toString() : null,
                membershipType,
                accountHolder.getId(),
                accountHolder.getAccountType() == com.ccapp.identity.account_holder.domain.enums.AccountType.ASSISTED,
                delegatedAccess
        );
    }

    private MobileHomeDashboardResponse.EngagementSection buildEngagement(UUID accountHolderId, List<TaskResponse> allTasks) {
        List<MobileHomeDashboardResponse.TodayTask> todayTasks = allTasks.stream()
                .filter(task -> task.type() != null && task.type().progressTracked())
                .limit(TODAY_TASK_LIMIT)
                .map(task -> new MobileHomeDashboardResponse.TodayTask(
                        task.id(),
                        task.title(),
                        task.status() == TaskStatus.COMPLETED,
                        task.type().name()
                ))
                .toList();

        int completed = Math.toIntExact(todayTasks.stream()
                .filter(MobileHomeDashboardResponse.TodayTask::completed)
                .count());

        return new MobileHomeDashboardResponse.EngagementSection(
                calculateStreakDays(accountHolderId),
                todayTasks,
                new MobileHomeDashboardResponse.ProgressSection(completed, todayTasks.size())
        );
    }

    private MobileHomeDashboardResponse.NextActionSection buildNextAction(AccountHolder accountHolder,
                                                                          UserProfile profile,
                                                                          MyCircleResponse myCircle,
                                                                          boolean hasActiveMembership,
                                                                          MobilePlanSelection planSelection,
                                                                          List<TaskResponse> allTasks) {
        List<TaskResponse> rankedActiveTasks = allTasks.stream()
                .filter(task -> task.status() != null && task.status().active())
                .sorted(Comparator.comparingInt(TaskResponse::priority).reversed()
                        .thenComparing(TaskResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskResponse::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (rankedActiveTasks.stream().anyMatch(task -> task.type() == TaskType.REPAY_INSTALLMENT)) {
            return new MobileHomeDashboardResponse.NextActionSection(
                    "PAY_REPAYMENT",
                    "Rembourser ton echeance",
                    "Une echeance est en attente de paiement.",
                    "Payer maintenant",
                    "OPEN_REPAYMENT"
            );
        }

        if (accountHolder.getKycStatus() != KycStatus.VERIFIED) {
            return new MobileHomeDashboardResponse.NextActionSection(
                    "COMPLETE_KYC",
                    "Finaliser ton KYC",
                    "Verifie ton identite pour debloquer toutes les fonctionnalites.",
                    "Completer",
                    "OPEN_KYC"
            );
        }

        if (myCircle == null) {
            return new MobileHomeDashboardResponse.NextActionSection(
                    "JOIN_CIRCLE",
                    "Rejoins un cercle",
                    "Entre dans un cercle pour avancer avec ta communaute.",
                    "Voir les cercles",
                    "OPEN_CIRCLES"
            );
        }

        if (!hasActiveMembership) {
            if (profile == null || !profile.isOnboardingCompleted()) {
                return new MobileHomeDashboardResponse.NextActionSection(
                        "COMPLETE_ONBOARDING",
                        "Complete ton onboarding",
                        "Ajoute les informations manquantes pour terminer ton inscription.",
                        "Continuer",
                        "OPEN_ONBOARDING"
                );
            }
            if (planSelection == null) {
                return new MobileHomeDashboardResponse.NextActionSection(
                        "SELECT_PLAN",
                        "Choisir une formule",
                        "Selectionne la formule qui correspond a ton parcours.",
                        "Choisir",
                        "OPEN_SUBSCRIPTION_PLANS"
                );
            }
            if (planSelection.getStatus() == MobilePlanSelectionStatus.PENDING_PAYMENT) {
                return new MobileHomeDashboardResponse.NextActionSection(
                        "PAY_SUBSCRIPTION",
                        "Payer ta formule",
                        "Finalise ton abonnement pour continuer.",
                        "Payer",
                        "OPEN_SUBSCRIPTION_PAYMENT"
                );
            }
            if (planSelection.getStatus() == MobilePlanSelectionStatus.READY_FOR_ACTIVATION) {
                return new MobileHomeDashboardResponse.NextActionSection(
                        "ACTIVATE_FREE_PLAN",
                        "Activer ta formule gratuite",
                        "Ta formule gratuite est selectionnee et peut etre activee.",
                        "Activer",
                        "ACTIVATE_FREE_PLAN"
                );
            }
        }

        return rankedActiveTasks.stream()
                .findFirst()
                .map(this::mapTaskToNextAction)
                .orElse(null);
    }

    private MobileHomeDashboardResponse.CircleSection buildCircle(MyCircleResponse myCircle) {
        if (myCircle == null) {
            return new MobileHomeDashboardResponse.CircleSection(false, List.of());
        }

        List<CircleActivityResponse> recentActivities = circleActivityService.getCircleActivity(myCircle.id()).stream()
                .limit(CIRCLE_ACTIVITY_LIMIT)
                .toList();

        Map<UUID, AppUser> appUsersById = appUserRepository.findAllById(recentActivities.stream()
                        .map(CircleActivityResponse::actorUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        Set<UUID> accountHolderIds = appUsersById.values().stream()
                .map(AppUser::getAccountHolderId)
                .collect(Collectors.toSet());

        Map<UUID, AccountHolder> accountHoldersById = accountHolderRepository.findAllById(accountHolderIds).stream()
                .collect(Collectors.toMap(AccountHolder::getId, Function.identity()));

        List<MobileHomeDashboardResponse.RecentActivity> mappedActivities = recentActivities.stream()
                .map(activity -> new MobileHomeDashboardResponse.RecentActivity(
                        resolveActivityUserName(activity.actorUserId(), appUsersById, accountHoldersById),
                        activity.message(),
                        activity.createdAt()
                ))
                .toList();

        return new MobileHomeDashboardResponse.CircleSection(true, mappedActivities);
    }

    private MobileHomeDashboardResponse.FinancingSection buildFinancing(UserProfile profile,
                                                                        CreditEligibilityResponse eligibility) {
        BigDecimal targetAmount = defaultAmount(eligibility.maxEligibleAmount());
        BigDecimal availableCapital = profile != null && profile.getAvailableCapital() != null
                ? profile.getAvailableCapital()
                : BigDecimal.ZERO;
        BigDecimal remainingAmount = targetAmount.subtract(availableCapital);
        if (remainingAmount.signum() < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        return new MobileHomeDashboardResponse.FinancingSection(
                defaultAmount(eligibility.eligibilityScore()).setScale(0, RoundingMode.HALF_UP).intValue(),
                remainingAmount,
                targetAmount
        );
    }

    private MobileHomeDashboardResponse.NextActionSection mapTaskToNextAction(TaskResponse task) {
        return switch (task.type()) {
            case COMPLETE_KYC -> new MobileHomeDashboardResponse.NextActionSection(
                    "COMPLETE_KYC",
                    task.title(),
                    task.description(),
                    "Completer",
                    "OPEN_KYC"
            );
            case JOIN_CIRCLE -> new MobileHomeDashboardResponse.NextActionSection(
                    "JOIN_CIRCLE",
                    task.title(),
                    task.description(),
                    "Voir les cercles",
                    "OPEN_CIRCLES"
            );
            case MAKE_FIRST_CONTRIBUTION -> new MobileHomeDashboardResponse.NextActionSection(
                    "MAKE_CONTRIBUTION",
                    task.title(),
                    task.description(),
                    "Contribuer",
                    "OPEN_CONTRIBUTION"
            );
            case INVITE_MEMBER -> new MobileHomeDashboardResponse.NextActionSection(
                    "INVITE_MEMBER",
                    task.title(),
                    task.description(),
                    "Inviter",
                    "OPEN_INVITES"
            );
            case APPLY_LOAN -> new MobileHomeDashboardResponse.NextActionSection(
                    "APPLY_FINANCING",
                    task.title(),
                    task.description(),
                    "Demander",
                    "OPEN_FINANCING"
            );
            case REPAY_INSTALLMENT -> new MobileHomeDashboardResponse.NextActionSection(
                    "PAY_REPAYMENT",
                    task.title(),
                    task.description(),
                    "Payer maintenant",
                    "OPEN_REPAYMENT"
            );
            case COMPLETE_PROFILE -> new MobileHomeDashboardResponse.NextActionSection(
                    "COMPLETE_ONBOARDING",
                    task.title(),
                    task.description(),
                    "Continuer",
                    "OPEN_ONBOARDING"
            );
            case VERIFY_PHONE -> new MobileHomeDashboardResponse.NextActionSection(
                    "VERIFY_PHONE",
                    task.title(),
                    task.description(),
                    "Verifier",
                    "OPEN_PHONE_VERIFICATION"
            );
            default -> null;
        };
    }

    private MyCircleResponse findMyCircle(UUID accountHolderId) {
        try {
            return circleService.getMyCircle(accountHolderId);
        } catch (NotFoundException ex) {
            return null;
        }
    }

    private UserProfile resolveProfile(UUID accountHolderId) {
        return appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId)
                .flatMap(appUser -> userProfileRepository.findByUserId(appUser.getId()))
                .orElse(null);
    }

    private boolean isActiveMembership(Membership membership) {
        return membership.getStatus() == MembershipStatus.ACTIVE
                && (membership.getExpiresAt() == null || membership.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    private int calculateStreakDays(UUID accountHolderId) {
        Set<LocalDate> completedDates = userTaskRepository.findByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).stream()
                .map(UserTask::getCompletedAt)
                .filter(Objects::nonNull)
                .map(OffsetDateTime::toLocalDate)
                .collect(Collectors.toSet());

        if (completedDates.isEmpty()) {
            return 0;
        }

        LocalDate cursor = LocalDate.now();
        if (!completedDates.contains(cursor)) {
            cursor = cursor.minusDays(1);
            if (!completedDates.contains(cursor)) {
                return 0;
            }
        }

        int streak = 0;
        while (completedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private String resolveActivityUserName(UUID actorUserId,
                                           Map<UUID, AppUser> appUsersById,
                                           Map<UUID, AccountHolder> accountHoldersById) {
        if (actorUserId == null) {
            return "Unknown";
        }
        AppUser appUser = appUsersById.get(actorUserId);
        if (appUser == null) {
            return "Unknown";
        }
        AccountHolder accountHolder = accountHoldersById.get(appUser.getAccountHolderId());
        if (accountHolder == null) {
            return "Unknown";
        }
        return displayName(accountHolder.getFirstName(), accountHolder.getLastName());
    }

    private String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean isDelegatedAccess(UUID actorUserId, UUID targetAccountHolderId) {
        if (actorUserId == null || targetAccountHolderId == null) {
            return false;
        }
        return appUserRepository.findById(actorUserId)
                .map(appUser -> !targetAccountHolderId.equals(appUser.getAccountHolderId()))
                .orElse(!targetAccountHolderId.equals(actorUserId));
    }
}

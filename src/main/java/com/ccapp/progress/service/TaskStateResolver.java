package com.ccapp.progress.service;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleContributionStatus;
import com.ccapp.circles.repository.CircleContributionRepository;
import com.ccapp.circles.repository.CircleInviteRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.credit.domain.entity.Loan;
import com.ccapp.credit.domain.entity.RepaymentSchedule;
import com.ccapp.credit.domain.enums.InstallmentStatus;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.credit.repository.RepaymentScheduleRepository;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import com.ccapp.financing.repository.LoanApplicationRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.identity.verification.domain.enums.PhoneVerificationStatus;
import com.ccapp.identity.verification.repository.PhoneVerificationRepository;
import com.ccapp.payments.operation.domain.enums.OperationStatus;
import com.ccapp.payments.operation.domain.enums.OperationType;
import com.ccapp.payments.operation.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskStateResolver {

    private static final List<LoanStatus> ACTIVE_REPAYMENT_LOAN_STATUSES = List.of(LoanStatus.ACTIVE, LoanStatus.DISBURSED);
    private static final List<InstallmentStatus> OPEN_INSTALLMENT_STATUSES = List.of(InstallmentStatus.PENDING, InstallmentStatus.LATE);
    private static final List<LoanApplicationStatus> ACTIVE_APPLICATION_STATUSES = List.of(
            LoanApplicationStatus.SUBMITTED,
            LoanApplicationStatus.UNDER_REVIEW,
            LoanApplicationStatus.APPROVED,
            LoanApplicationStatus.DISBURSED,
            LoanApplicationStatus.REPAID
    );

    private final AccountHolderRepository accountHolderRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final CircleRepository circleRepository;
    private final CircleContributionRepository circleContributionRepository;
    private final CircleInviteRepository circleInviteRepository;
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CountryConfigResolver countryConfigResolver;
    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public TaskUserState resolve(UUID accountHolderId) {
        AccountHolder accountHolder = accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(accountHolderId);
        UUID activeCircleId = activeCircles.stream()
                .findFirst()
                .map(Circle::getId)
                .orElse(null);

        boolean phoneVerified = hasText(accountHolder.getPhoneNumber())
                && phoneVerificationRepository.existsByRequesterAccountHolderIdAndPhoneNumberAndStatus(
                accountHolderId,
                accountHolder.getPhoneNumber(),
                PhoneVerificationStatus.VERIFIED
        );

        boolean profileComplete = hasText(accountHolder.getFirstName())
                && hasText(accountHolder.getLastName())
                && hasText(accountHolder.getPhoneNumber())
                && hasText(accountHolder.getCountryCode())
                && hasText(accountHolder.getServiceCountryCode());
        boolean walletActivated = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE").isPresent();
        boolean walletFunded = operationRepository.findFirstByAccountHolderIdAndTypeAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                OperationType.WALLET_DEPOSIT_PSP,
                OperationStatus.CONFIRMED
        ).isPresent() || operationRepository.findFirstByAccountHolderIdAndTypeAndStatusOrderByCreatedAtDesc(
                accountHolderId,
                OperationType.WALLET_DEPOSIT_AGENT,
                OperationStatus.CONFIRMED
        ).isPresent();

        boolean madeFirstContribution = circleContributionRepository.countCompletedByAccountHolderId(
                accountHolderId,
                List.of(CircleContributionStatus.PAID, CircleContributionStatus.LATE)
        ) > 0;

        boolean appliedLoan = loanRepository.countByAccountHolderIdAndStatusIn(
                accountHolderId,
                List.of(
                        LoanStatus.PENDING,
                        LoanStatus.APPROVED,
                        LoanStatus.DISBURSED,
                        LoanStatus.ACTIVE,
                        LoanStatus.REPAID,
                        LoanStatus.DEFAULTED
                )
        ) > 0 || loanApplicationRepository.countByAccountHolderIdAndStatusIn(accountHolderId, ACTIVE_APPLICATION_STATUSES) > 0;

        boolean financingEnabled = hasText(accountHolder.getServiceCountryCode())
                && countryConfigResolver.resolveActiveConfig(accountHolder.getServiceCountryCode(), CountryConfigType.FINANCING).source()
                != CountryConfigResolver.ConfigSource.EMPTY;

        return new TaskUserState(
                accountHolder,
                phoneVerified,
                profileComplete,
                walletActivated,
                walletFunded,
                accountHolder.getKycStatus() == KycStatus.VERIFIED,
                !activeCircles.isEmpty(),
                activeCircleId,
                madeFirstContribution,
                circleInviteRepository.countByCreatedByAccountHolderId(accountHolderId) > 0,
                appliedLoan,
                financingEnabled,
                resolveRepaymentSnapshot(accountHolderId)
        );
    }

    private RepaymentSnapshot resolveRepaymentSnapshot(UUID accountHolderId) {
        Loan selectedLoan = null;
        RepaymentSchedule selectedSchedule = null;

        for (Loan loan : loanRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)) {
            if (!ACTIVE_REPAYMENT_LOAN_STATUSES.contains(loan.getStatus())) {
                continue;
            }
            RepaymentSchedule schedule = repaymentScheduleRepository
                    .findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(loan.getId(), OPEN_INSTALLMENT_STATUSES)
                    .orElse(null);
            if (schedule == null) {
                continue;
            }

            if (selectedSchedule == null || schedule.getDueDate().isBefore(selectedSchedule.getDueDate())) {
                selectedLoan = loan;
                selectedSchedule = schedule;
            }
        }

        if (selectedLoan == null || selectedSchedule == null) {
            return null;
        }

        OffsetDateTime dueAt = selectedSchedule.getDueDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        boolean overdue = selectedSchedule.getStatus() == InstallmentStatus.LATE
                || selectedSchedule.getDueDate().isBefore(LocalDate.now());

        return new RepaymentSnapshot(
                selectedLoan.getId(),
                selectedSchedule.getInstallmentNumber(),
                dueAt,
                overdue
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record TaskUserState(
            AccountHolder accountHolder,
            boolean phoneVerified,
            boolean profileComplete,
            boolean walletActivated,
            boolean walletFunded,
            boolean kycCompleted,
            boolean joinedCircle,
            UUID activeCircleId,
            boolean madeFirstContribution,
            boolean invitedMember,
            boolean appliedLoan,
            boolean financingEnabled,
            RepaymentSnapshot repaymentSnapshot
    ) {
    }

    public record RepaymentSnapshot(
            UUID loanId,
            int installmentNumber,
            OffsetDateTime dueAt,
            boolean overdue
    ) {
    }
}

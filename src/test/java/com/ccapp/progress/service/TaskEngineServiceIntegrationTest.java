package com.ccapp.progress.service;

import com.ccapp.common.events.DomainEvent;
import com.ccapp.common.events.IdempotentEventListenerExecutor;
import com.ccapp.config.TestJacksonAndCachingConfig;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.notifications.domain.enums.NotificationType;
import com.ccapp.notifications.dto.CreateNotificationRequest;
import com.ccapp.notifications.dto.SendMultiChannelNotificationRequest;
import com.ccapp.notifications.service.NotificationService;
import com.ccapp.progress.domain.entity.TaskDefinition;
import com.ccapp.progress.domain.entity.UserTask;
import com.ccapp.progress.domain.enums.TaskStatus;
import com.ccapp.progress.domain.enums.TaskTriggerEvent;
import com.ccapp.progress.domain.enums.TaskType;
import com.ccapp.progress.dto.TaskResponse;
import com.ccapp.progress.event.AccountHolderCreatedEvent;
import com.ccapp.progress.repository.TaskAuditLogRepository;
import com.ccapp.progress.repository.TaskDefinitionRepository;
import com.ccapp.progress.repository.UserTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        TaskEngineService.class,
        TaskAuditService.class,
        TaskEventListener.class,
        com.ccapp.notifications.template.NotificationTemplateRenderer.class,
        TestJacksonAndCachingConfig.class
})
class TaskEngineServiceIntegrationTest {

    @Autowired
    private TaskEngineService taskEngineService;
    @Autowired
    private TaskEventListener taskEventListener;
    @Autowired
    private UserTaskRepository userTaskRepository;
    @Autowired
    private TaskDefinitionRepository taskDefinitionRepository;
    @Autowired
    private TaskAuditLogRepository taskAuditLogRepository;

    @MockBean
    private TaskStateResolver taskStateResolver;
    @MockBean
    private NotificationService notificationService;
    @MockBean
    private IdempotentEventListenerExecutor listenerExecutor;

    private final Map<UUID, TaskStateResolver.TaskUserState> states = new HashMap<>();

    @BeforeEach
    void setUp() {
        states.clear();
        seedDefinitions();

        when(taskStateResolver.resolve(any())).thenAnswer(invocation -> {
            UUID accountHolderId = invocation.getArgument(0);
            TaskStateResolver.TaskUserState state = states.get(accountHolderId);
            if (state == null) {
                throw new IllegalStateException("Missing task state for " + accountHolderId);
            }
            return state;
        });

        doAnswer(invocation -> {
            Runnable handler = invocation.getArgument(2);
            handler.run();
            return null;
        }).when(listenerExecutor).execute(anyString(), any(DomainEvent.class), any(Runnable.class));
    }

    @Test
    void accountHolderCreatedEvent_shouldCreateTasksAndTriggerSingleNotification() {
        UUID accountHolderId = UUID.randomUUID();
        states.put(accountHolderId, state(
                accountHolderId,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null
        ));

        taskEventListener.on(AccountHolderCreatedEvent.create(accountHolderId));

        List<UserTask> activeTasks = userTaskRepository.findRankedByAccountHolderIdAndStatuses(
                accountHolderId,
                TaskStatus.activeStatuses()
        );

        assertEquals(3, activeTasks.size());
        assertEquals(TaskType.COMPLETE_KYC, activeTasks.get(0).getTaskType());
        assertEquals(TaskType.VERIFY_PHONE, activeTasks.get(1).getTaskType());
        assertEquals(TaskType.COMPLETE_PROFILE, activeTasks.get(2).getTaskType());
        assertTrue(taskAuditLogRepository.count() >= 4);

        ArgumentCaptor<SendMultiChannelNotificationRequest> captor = ArgumentCaptor.forClass(SendMultiChannelNotificationRequest.class);
        verify(notificationService).sendMultiChannel(captor.capture());
        assertEquals(NotificationType.TASK_AVAILABLE, captor.getValue().type());
        assertEquals(accountHolderId, captor.getValue().accountHolderId());
        assertEquals("COMPLETE_KYC", captor.getValue().message());
    }

    @Test
    void createTask_shouldPreventDuplicateActiveTaskPerType() {
        UUID accountHolderId = UUID.randomUUID();
        states.put(accountHolderId, state(
                accountHolderId,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null
        ));

        TaskResponse first = taskEngineService.createTask(accountHolderId, TaskType.COMPLETE_PROFILE, null);
        TaskResponse second = taskEngineService.createTask(accountHolderId, TaskType.COMPLETE_PROFILE, null);

        assertEquals(first.id(), second.id());
        assertEquals(1, userTaskRepository.findByAccountHolderIdAndTaskTypeOrderByCreatedAtDesc(accountHolderId, TaskType.COMPLETE_PROFILE).size());

        verify(notificationService).sendMultiChannel(any(SendMultiChannelNotificationRequest.class));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void getNextBestAction_shouldPrioritizeOverdueRepayment() {
        UUID accountHolderId = UUID.randomUUID();
        states.put(accountHolderId, state(
                accountHolderId,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                new TaskStateResolver.RepaymentSnapshot(
                        UUID.randomUUID(),
                        3,
                        OffsetDateTime.now().minusDays(1),
                        true
                )
        ));

        List<TaskResponse> visibleTasks = taskEngineService.getUserTasks(accountHolderId);
        TaskResponse nextBestAction = taskEngineService.getNextBestAction(accountHolderId);

        assertEquals(3, visibleTasks.size());
        assertEquals(TaskType.REPAY_INSTALLMENT, visibleTasks.get(0).type());
        assertNotNull(nextBestAction);
        assertEquals(TaskType.REPAY_INSTALLMENT, nextBestAction.type());
        assertNotNull(nextBestAction.dueDate());
        assertTrue(nextBestAction.priority() > visibleTasks.get(1).priority());
    }

    @Test
    void completeTask_shouldMarkCompletedCreateFollowUpAndNotify() {
        UUID accountHolderId = UUID.randomUUID();
        states.put(accountHolderId, state(
                accountHolderId,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                null
        ));

        TaskResponse task = taskEngineService.createTask(accountHolderId, TaskType.COMPLETE_KYC, null);

        reset(notificationService);
        states.put(accountHolderId, state(
                accountHolderId,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                null
        ));

        TaskResponse completed = taskEngineService.completeTask(task.id());

        UserTask persisted = userTaskRepository.findById(completed.id()).orElseThrow();
        UserTask followUp = userTaskRepository
                .findTopByAccountHolderIdAndTaskTypeAndStatusInOrderByCreatedAtDesc(
                        accountHolderId,
                        TaskType.JOIN_CIRCLE,
                        TaskStatus.activeStatuses()
                )
                .orElse(null);

        assertEquals(TaskStatus.COMPLETED, persisted.getStatus());
        assertNotNull(persisted.getCompletedAt());
        assertNotNull(followUp);
        assertEquals(TaskType.COMPLETE_KYC, completed.type());

        ArgumentCaptor<CreateNotificationRequest> completionCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        ArgumentCaptor<SendMultiChannelNotificationRequest> creationCaptor = ArgumentCaptor.forClass(SendMultiChannelNotificationRequest.class);

        verify(notificationService).createNotification(completionCaptor.capture());
        verify(notificationService).sendMultiChannel(creationCaptor.capture());

        assertEquals(NotificationType.TASK_COMPLETED, completionCaptor.getValue().type());
        assertEquals(NotificationType.TASK_AVAILABLE, creationCaptor.getValue().type());
        assertEquals("JOIN_CIRCLE", creationCaptor.getValue().message());
    }

    @Test
    void getUserTasks_shouldReturnAtMostThreeVisibleTasks() {
        UUID accountHolderId = UUID.randomUUID();
        states.put(accountHolderId, state(
                accountHolderId,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null
        ));

        taskEngineService.createTask(accountHolderId, TaskType.INVITE_MEMBER, null);
        taskEngineService.createTask(accountHolderId, TaskType.COMPLETE_PROFILE, null);
        taskEngineService.createTask(accountHolderId, TaskType.VERIFY_PHONE, null);
        taskEngineService.createTask(accountHolderId, TaskType.COMPLETE_KYC, null);

        List<TaskResponse> visibleTasks = taskEngineService.getUserTasks(accountHolderId);

        assertEquals(3, visibleTasks.size());
        assertEquals(TaskType.COMPLETE_KYC, visibleTasks.get(0).type());
        assertEquals(TaskType.VERIFY_PHONE, visibleTasks.get(1).type());
        assertEquals(TaskType.COMPLETE_PROFILE, visibleTasks.get(2).type());
    }

    private void seedDefinitions() {
        taskDefinitionRepository.saveAll(List.of(
                definition(TaskType.COMPLETE_KYC, TaskTriggerEvent.ACCOUNT_HOLDER_CREATED),
                definition(TaskType.VERIFY_PHONE, TaskTriggerEvent.PHONE_VERIFIED),
                definition(TaskType.COMPLETE_PROFILE, TaskTriggerEvent.MANUAL),
                definition(TaskType.JOIN_CIRCLE, TaskTriggerEvent.CIRCLE_JOINED),
                definition(TaskType.INVITE_MEMBER, TaskTriggerEvent.CIRCLE_JOINED),
                definition(TaskType.MAKE_FIRST_CONTRIBUTION, TaskTriggerEvent.CONTRIBUTION_RECEIVED),
                definition(TaskType.APPLY_LOAN, TaskTriggerEvent.LOAN_APPLIED),
                definition(TaskType.REPAY_INSTALLMENT, TaskTriggerEvent.REPAYMENT_DUE)
        ));
    }

    private TaskDefinition definition(TaskType taskType, TaskTriggerEvent triggerEvent) {
        String actionJson = taskType == TaskType.REPAY_INSTALLMENT
                ? "{\"type\":\"FLOW\",\"flow\":\"LOAN_REPAYMENT_FLOW\",\"params\":{\"loanId\":\"{{loanId}}\"}}"
                : "{\"type\":\"FLOW\",\"flow\":\"" + taskType.name() + "_FLOW\"}";

        return TaskDefinition.builder()
                .taskType(taskType)
                .titleTemplate(taskType.name())
                .descriptionTemplate(taskType.name() + "_DESCRIPTION")
                .triggerEvent(triggerEvent)
                .actionJson(actionJson)
                .rewardPoints(10)
                .active(true)
                .metadataJson("{\"basePriority\":" + taskType.defaultPriority() + "}")
                .build();
    }

    private TaskStateResolver.TaskUserState state(UUID accountHolderId,
                                                  boolean kycCompleted,
                                                  boolean phoneVerified,
                                                  boolean profileComplete,
                                                  boolean joinedCircle,
                                                  boolean madeFirstContribution,
                                                  boolean invitedMember,
                                                  boolean financingEnabled,
                                                  TaskStateResolver.RepaymentSnapshot repaymentSnapshot) {
        AccountHolder accountHolder = AccountHolder.builder()
                .id(accountHolderId)
                .firstName("Grace")
                .lastName("Mbono")
                .phoneNumber(phoneVerified ? "+22997000000" : "+22996000000")
                .countryCode("BJ")
                .serviceCountryCode("BJ")
                .accountType(AccountType.STANDARD)
                .createdVia(AccountCreatedVia.SELF)
                .kycStatus(kycCompleted ? KycStatus.VERIFIED : KycStatus.NOT_STARTED)
                .build();

        return new TaskStateResolver.TaskUserState(
                accountHolder,
                phoneVerified,
                profileComplete,
                false,
                false,
                kycCompleted,
                joinedCircle,
                joinedCircle ? UUID.randomUUID() : null,
                madeFirstContribution,
                invitedMember,
                false,
                financingEnabled,
                repaymentSnapshot
        );
    }
}

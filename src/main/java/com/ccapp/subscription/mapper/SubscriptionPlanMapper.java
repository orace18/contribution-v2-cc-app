package com.ccapp.subscription.mapper;

import com.ccapp.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.subscription.dto.SubscriptionPlanResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionPlanMapper {
    SubscriptionPlanResponse toResponse(SubscriptionPlan plan);
}

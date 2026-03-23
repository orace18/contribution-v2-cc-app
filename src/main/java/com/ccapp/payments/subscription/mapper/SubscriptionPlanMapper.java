package com.ccapp.payments.subscription.mapper;

import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.dto.SubscriptionPlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionPlanMapper {
    @Mapping(target = "countryCode", source = "country")
    @Mapping(target = "unitAmount", source = "price")
    @Mapping(target = "grade", expression = "java(plan.getGrade() == null ? null : plan.getGrade().name())")
    SubscriptionPlanResponse toResponse(SubscriptionPlan plan);
}

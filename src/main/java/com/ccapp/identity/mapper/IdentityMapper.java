package com.ccapp.identity.mapper;

import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.domain.entity.UserProfile;
import com.ccapp.identity.domain.entity.UserSession;
import com.ccapp.identity.dto.UserDeviceDto;
import com.ccapp.identity.dto.UserProfileDto;
import com.ccapp.identity.dto.UserSessionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IdentityMapper {

    UserProfileDto toDto(UserProfile profile);

    UserDeviceDto toDto(UserDevice device);

    @Mapping(target = "deviceId", source = "device.id")
    UserSessionDto toDto(UserSession session);
}

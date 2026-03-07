package com.ccapp.identity.device.service;

import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional(readOnly = true)
    public List<UserDevice> getDevicesByAppUserId(UUID appUserId) {
        return userDeviceRepository.findByUserIdAndDeletedFalse(appUserId);
    }
}

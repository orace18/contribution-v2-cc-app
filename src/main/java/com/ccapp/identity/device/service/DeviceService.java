package com.ccapp.identity.device.service;

import com.ccapp.identity.domain.entity.UserDevice;
import com.ccapp.identity.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional(readOnly = true)
    public List<UserDevice> getDevicesByAppUserId(UUID appUserId) {
        log.debug("Identity device list appUserId={}", appUserId);
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndDeletedFalse(appUserId);
        log.debug("Identity device list result appUserId={} count={}", appUserId, devices.size());
        return devices;
    }
}

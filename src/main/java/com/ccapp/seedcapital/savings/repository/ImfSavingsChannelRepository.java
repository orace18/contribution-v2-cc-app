package com.ccapp.seedcapital.savings.repository;

import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImfSavingsChannelRepository extends JpaRepository<ImfSavingsChannel, UUID> {

    Optional<ImfSavingsChannel> findFirstByAccountHolderIdAndCountryCodeOrderByUpdatedAtDesc(UUID accountHolderId,
                                                                                              String countryCode);

    List<ImfSavingsChannel> findByAccountHolderIdInAndCountryCodeOrderByUpdatedAtDesc(Collection<UUID> accountHolderIds,
                                                                                       String countryCode);
}

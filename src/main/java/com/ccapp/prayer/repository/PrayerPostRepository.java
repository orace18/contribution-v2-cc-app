package com.ccapp.prayer.repository;

import com.ccapp.prayer.domain.entity.PrayerPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrayerPostRepository extends JpaRepository<PrayerPost, UUID> {
}

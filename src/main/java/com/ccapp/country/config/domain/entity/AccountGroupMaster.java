package com.ccapp.country.config.domain.entity;

import com.ccapp.country.config.domain.enums.CountryChartAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_groups_master")
public class AccountGroupMaster {

    @Id
    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CountryChartAccountType type;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system_required", nullable = false)
    private boolean systemRequired;
}

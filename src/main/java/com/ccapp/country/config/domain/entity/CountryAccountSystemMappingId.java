package com.ccapp.country.config.domain.entity;

import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CountryAccountSystemMappingId implements Serializable {

    private String countryCode;
    private CountryAccountSystemTag systemTag;
}

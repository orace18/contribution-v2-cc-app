package com.ccapp.mobile.sponsor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneValidatorTest {

    @Test
    void normalizeToE164_keepsPhoneThatAlreadyStartsWithPlus() {
        assertThat(PhoneValidator.normalizeToE164("+22966000001", "BJ"))
                .isEqualTo("+22966000001");
    }

    @Test
    void normalizeToE164_addsPlusWhenCountryCodeAlreadyPresent() {
        assertThat(PhoneValidator.normalizeToE164("22966000001", "BJ"))
                .isEqualTo("+22966000001");
    }

    @Test
    void normalizeToE164_prefixesCountryCallingCodeForLocalNumber() {
        assertThat(PhoneValidator.normalizeToE164("66000001", "BJ"))
                .isEqualTo("+22966000001");
    }
}

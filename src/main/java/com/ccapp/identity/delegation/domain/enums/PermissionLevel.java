package com.ccapp.identity.delegation.domain.enums;

public enum PermissionLevel {
    VIEW,
    TRANSACT,
    FULL;

    public boolean allows(PermissionLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}

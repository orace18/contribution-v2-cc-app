package com.ccapp.wallet.service;

public enum WalletState {
    VERIFY_PHONE,
    WAIT_MEMBERSHIP_ACTIVATION,
    WAIT_WALLET_ACTIVATION,
    WALLET_ACTIVE,
    WALLET_ACTIVATION_BLOCKED
}

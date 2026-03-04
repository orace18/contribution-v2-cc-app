package com.ccapp.identity.service;

public interface FirebaseTokenVerifier {
    FirebaseUserClaims verify(String token);
}

package com.fariha.chattingapp.service;

public interface SmsSender {
    void sendVerificationCode(String phoneNumber, String code);

    default boolean exposesDebugCode() {
        return false;
    }
}

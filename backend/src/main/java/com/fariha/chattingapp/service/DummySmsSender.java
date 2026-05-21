package com.fariha.chattingapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "dummy", matchIfMissing = true)
public class DummySmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(DummySmsSender.class);

    @Override
    public void sendVerificationCode(String phoneNumber, String code) {
        log.info("Dummy SMS verification code for {} is {}", phoneNumber, code);
    }

    @Override
    public boolean exposesDebugCode() {
        return true;
    }
}

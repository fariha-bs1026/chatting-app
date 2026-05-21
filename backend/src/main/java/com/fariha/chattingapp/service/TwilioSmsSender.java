package com.fariha.chattingapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {
    private final RestClient restClient;
    private final String accountSid;
    private final String fromNumber;

    public TwilioSmsSender(
            RestClient.Builder restClientBuilder,
            @Value("${app.sms.twilio.account-sid}") String accountSid,
            @Value("${app.sms.twilio.auth-token}") String authToken,
            @Value("${app.sms.twilio.from-number}") String fromNumber
    ) {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            throw new IllegalStateException("Twilio SMS is enabled, but Twilio credentials are not configured");
        }
        this.accountSid = accountSid;
        this.fromNumber = fromNumber;
        this.restClient = restClientBuilder
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeaders(headers -> headers.setBasicAuth(accountSid, authToken))
                .build();
    }

    @Override
    public void sendVerificationCode(String phoneNumber, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneNumber);
        form.add("From", fromNumber);
        form.add("Body", "Your ChatFlow verification code is " + code + ". It expires soon.");

        restClient.post()
                .uri("/Accounts/{accountSid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}

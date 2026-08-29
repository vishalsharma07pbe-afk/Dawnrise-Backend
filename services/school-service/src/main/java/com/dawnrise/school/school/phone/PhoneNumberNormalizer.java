package com.dawnrise.school.school.phone;

import com.dawnrise.school.school.exception.FieldValidationException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PhoneNumberNormalizer {

    private static final String INVALID_PHONE_MESSAGE =
            "Phone number must be valid";

    private final PhoneNumberUtil phoneNumberUtil =
            PhoneNumberUtil.getInstance();

    public String normalizeRequired(
            String field,
            String rawPhone
    ) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw invalid(field);
        }

        try {
            var parsed = phoneNumberUtil.parse(
                    rawPhone,
                    null
            );

            if (!phoneNumberUtil.isValidNumber(parsed)) {
                throw invalid(field);
            }

            return phoneNumberUtil.format(
                    parsed,
                    PhoneNumberUtil.PhoneNumberFormat.E164
            );
        } catch (NumberParseException exception) {
            throw invalid(field);
        }
    }

    private FieldValidationException invalid(String field) {
        return new FieldValidationException(
                "Request validation failed",
                Map.of(field, INVALID_PHONE_MESSAGE)
        );
    }
}

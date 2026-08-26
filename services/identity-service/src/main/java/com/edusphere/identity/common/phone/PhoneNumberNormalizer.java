package com.edusphere.identity.common.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    private final PhoneNumberUtil phoneNumberUtil =
            PhoneNumberUtil.getInstance();

    public String normalizeOptional(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return rawPhone;
        }

        try {
            var parsed = phoneNumberUtil.parse(
                    rawPhone,
                    null
            );

            if (!phoneNumberUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException(
                        "Authority phone number must be valid"
                );
            }

            return phoneNumberUtil.format(
                    parsed,
                    PhoneNumberUtil.PhoneNumberFormat.E164
            );
        } catch (NumberParseException exception) {
            throw new IllegalArgumentException(
                    "Authority phone number must be valid"
            );
        }
    }
}

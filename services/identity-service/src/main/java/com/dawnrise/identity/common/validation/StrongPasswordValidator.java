package com.dawnrise.identity.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MINIMUM_LENGTH = 8;
    private static final int MAXIMUM_LENGTH = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return false;
        }

        if (password.length() < MINIMUM_LENGTH
                || password.length() > MAXIMUM_LENGTH) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasNumber = false;
        boolean hasSpecialCharacter = false;

        for (char character : password.toCharArray()) {
            if (Character.isWhitespace(character)) {
                return false;
            }

            if (Character.isUpperCase(character)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(character)) {
                hasLowercase = true;
            } else if (Character.isDigit(character)) {
                hasNumber = true;
            } else {
                hasSpecialCharacter = true;
            }
        }

        return hasUppercase
                && hasLowercase
                && hasNumber
                && hasSpecialCharacter
                && !containsForbiddenSequence(password);
    }

    private boolean containsForbiddenSequence(String password) {
        String normalizedPassword = password.toLowerCase();

        for (int index = 0;
             index <= normalizedPassword.length() - 3;
             index++) {

            char first = normalizedPassword.charAt(index);
            char second = normalizedPassword.charAt(index + 1);
            char third = normalizedPassword.charAt(index + 2);

            if (first == second && second == third) {
                return true;
            }

            boolean allLetters =
                    Character.isLetter(first)
                            && Character.isLetter(second)
                            && Character.isLetter(third);

            boolean allNumbers =
                    Character.isDigit(first)
                            && Character.isDigit(second)
                            && Character.isDigit(third);

            if (!allLetters && !allNumbers) {
                continue;
            }

            boolean ascending =
                    second == first + 1
                            && third == second + 1;

            boolean descending =
                    second == first - 1
                            && third == second - 1;

            if (ascending || descending) {
                return true;
            }
        }

        return false;
    }
}
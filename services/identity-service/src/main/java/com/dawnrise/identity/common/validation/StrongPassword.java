package com.dawnrise.identity.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default
            "Password must contain 8 to 72 characters, "
                    + "including one uppercase letter, one lowercase letter, "
                    + "one number and one special character, "
                    + "without whitespace or a sequence of three characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
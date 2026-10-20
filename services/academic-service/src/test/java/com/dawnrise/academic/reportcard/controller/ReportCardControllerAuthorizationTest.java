package com.dawnrise.academic.reportcard.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCardControllerAuthorizationTest {
    @ParameterizedTest
    @ValueSource(strings = {"list", "detail", "pdf"})
    void staffReadsAllowGeneratorOrReviewer(String methodName) {
        Method method = findMethod(methodName);
        String expression = method.getAnnotation(PreAuthorize.class).value();

        assertThat(expression)
                .contains("REPORT_CARD_GENERATE")
                .contains("REPORT_CARD_REVIEW")
                .contains(" or ");
    }

    private static Method findMethod(String name) {
        return java.util.Arrays.stream(ReportCardController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}

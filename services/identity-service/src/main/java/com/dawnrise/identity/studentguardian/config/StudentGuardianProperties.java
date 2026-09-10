package com.dawnrise.identity.studentguardian.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "dawnrise.student-guardian")
public class StudentGuardianProperties {

    @Min(1)
    @Max(10)
    private int maxActiveGuardiansPerStudent;

    public int getMaxActiveGuardiansPerStudent() {
        return maxActiveGuardiansPerStudent;
    }

    public void setMaxActiveGuardiansPerStudent(
            int maxActiveGuardiansPerStudent
    ) {
        this.maxActiveGuardiansPerStudent =
                maxActiveGuardiansPerStudent;
    }
}

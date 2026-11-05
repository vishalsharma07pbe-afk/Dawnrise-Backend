package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.RequestInput;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class StaffLeavePreviewTransactionTest {
    @Test
    void previewAllowsBalanceCreation() throws Exception {
        var method = StaffLeaveService.class.getMethod(
                "preview", long.class, long.class, RequestInput.class);
        var transaction = method.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }
}

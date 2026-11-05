package com.dawnrise.school.leave;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffLeaveBalanceAvailabilityTest {
    @Test
    void availableBalanceIsReducedOnlyByApprovedUsedLeave(){
        assertEquals(new BigDecimal("8.00"),StaffLeaveService.availableBalance(
                new BigDecimal("1.00"),new BigDecimal("10.00"),new BigDecimal("2.00"),
                new BigDecimal("-1.00"),new BigDecimal("4.00")));
    }
}

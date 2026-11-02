package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.RequestKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveUnitCalculatorTest {
    private final LeaveUnitCalculator calculator=new LeaveUnitCalculator();
    @Test void fullDayUsesOnlyWorkingCalendarDates(){assertThat(calculator.calculate(RequestKind.DATE_RANGE,3,null,null)).isEqualByComparingTo("3");}
    @Test void halfDayUsesHalfUnit(){assertThat(calculator.calculate(RequestKind.HALF_DAY,1,null,null)).isEqualByComparingTo("0.50");}
    @Test void partialTimeRoundsUpToQuarterDay(){assertThat(calculator.calculate(RequestKind.PARTIAL_TIME,1,LocalTime.of(8,0),LocalTime.of(10,1))).isEqualByComparingTo(new BigDecimal("0.50"));}
    @Test void rejectsRangesWithoutWorkingDays(){assertThatThrownBy(()->calculator.calculate(RequestKind.FULL_DAY,0,null,null)).isInstanceOf(IllegalArgumentException.class);}
}

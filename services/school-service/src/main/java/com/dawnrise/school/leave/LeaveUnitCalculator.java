package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.RequestKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

@Component
public class LeaveUnitCalculator {
    public BigDecimal calculate(RequestKind kind,int workingDates,LocalTime start,LocalTime end){
        if(workingDates<=0)throw new IllegalArgumentException("The request contains no working days");
        return switch(kind){
            case HALF_DAY -> new BigDecimal("0.50");
            case LATE_ARRIVAL,EARLY_DEPARTURE,PARTIAL_TIME -> {
                if(start==null||end==null||!end.isAfter(start))throw new IllegalArgumentException("A valid partial-time range is required");
                long minutes=Duration.between(start,end).toMinutes();
                yield BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(480),2,RoundingMode.CEILING)
                        .multiply(new BigDecimal("4")).setScale(0,RoundingMode.CEILING).divide(new BigDecimal("4"));
            }
            default -> BigDecimal.valueOf(workingDates);
        };
    }
}

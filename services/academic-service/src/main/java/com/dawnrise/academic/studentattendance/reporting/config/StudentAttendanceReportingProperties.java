package com.dawnrise.academic.studentattendance.reporting.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(
        prefix = "dawnrise.student-attendance.reporting"
)
@Validated
public class StudentAttendanceReportingProperties {

    @Min(1)
    @Max(3660)
    private int maxDateRangeDays = 366;

    @Min(1)
    @Max(500)
    private int maxPageSize = 100;

    @Min(1)
    @Max(100000)
    private int maxExportRows = 10000;

    public int getMaxDateRangeDays() {
        return maxDateRangeDays;
    }

    public void setMaxDateRangeDays(
            int maxDateRangeDays
    ) {
        this.maxDateRangeDays = maxDateRangeDays;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(
            int maxPageSize
    ) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxExportRows() {
        return maxExportRows;
    }

    public void setMaxExportRows(
            int maxExportRows
    ) {
        this.maxExportRows = maxExportRows;
    }
}

package com.dawnrise.academic.studentattendance.reporting.export;

public enum StudentAttendanceReportExportFormat {

    CSV(
            "csv",
            "text/csv;charset=UTF-8"
    ),

    XLSX(
            "xlsx",
            "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet"
    );

    private final String fileExtension;
    private final String contentType;

    StudentAttendanceReportExportFormat(
            String fileExtension,
            String contentType
    ) {
        this.fileExtension = fileExtension;
        this.contentType = contentType;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public String contentType() {
        return contentType;
    }
}
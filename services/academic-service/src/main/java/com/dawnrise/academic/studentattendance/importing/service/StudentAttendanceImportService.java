package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.studentattendance.importing.dto.ConfirmStudentAttendanceImportRequest;
import com.dawnrise.academic.studentattendance.importing.dto.StudentAttendanceImportPreviewResponse;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StudentAttendanceImportService {

    byte[] csvTemplate();

    StudentAttendanceImportPreviewResponse preview(
            long organizationId,
            long actorUserId,
            MultipartFile file
    );

    StudentAttendanceOfflineSyncResponse confirm(
            long organizationId,
            long actorUserId,
            long previewId,
            String idempotencyKey,
            ConfirmStudentAttendanceImportRequest request
    );
}

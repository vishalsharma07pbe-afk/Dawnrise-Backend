# Results API Contract

This document covers the first Results increment only: subject mark sheets for
scheduled assessments and sections.

## Permissions

- `RESULT_VIEW`: view a subject mark sheet when assigned to the sheet section
  as either the subject teacher for the scheduled grade subject or the class
  teacher.
- `RESULT_MARK_ENTRY`: create/view the subject teacher mark-entry sheet, save
  marks, and submit the sheet. The actor must also be the assigned subject
  teacher for the sheet section and grade subject.
- `RESULT_REVIEW`: return or approve a submitted sheet. The actor must also be
  the assigned class teacher for the sheet section.

Tenant identity is always read from the JWT `organizationId` claim. Actor
identity is always read from the JWT subject.

## Worklist

List the authenticated teacher's Results dashboard tasks:

`GET /api/v1/results/worklist`

The endpoint requires `RESULT_VIEW`. It returns mark-entry tasks only when the
actor also has `RESULT_MARK_ENTRY`, and review tasks only when the actor also
has `RESULT_REVIEW`.

The response includes tasks even before a subject sheet has been created. Draft
examinations are not returned. Tasks are scoped to the actor's assignments:

- `MARK_ENTRY`: the actor is the assigned subject teacher for the section and
  grade subject.
- `REVIEW`: the actor is the assigned class teacher for the section.

Response body:

```json
[
  {
    "taskType": "MARK_ENTRY",
    "academicYearId": 2,
    "academicYearName": "2026-27",
    "examinationId": 3,
    "examinationName": "Mid-term",
    "scheduledAssessmentId": 6,
    "assessmentDate": "2026-10-05",
    "maximumMarks": 50.00,
    "gradeLevelId": 4,
    "gradeLevelCode": "G4",
    "gradeLevelName": "Grade 4",
    "sectionId": 7,
    "sectionCode": "A",
    "sectionName": "Section A",
    "gradeLevelSubjectId": 5,
    "subjectId": 8,
    "subjectCode": "MATH",
    "subjectName": "Mathematics",
    "subjectSheetId": null,
    "status": "NOT_CREATED",
    "sheetVersion": null,
    "totalStudents": 30,
    "completedMarks": 0
  },
  {
    "taskType": "REVIEW",
    "academicYearId": 2,
    "academicYearName": "2026-27",
    "examinationId": 3,
    "examinationName": "Mid-term",
    "scheduledAssessmentId": 9,
    "assessmentDate": "2026-10-06",
    "maximumMarks": 50.00,
    "gradeLevelId": 4,
    "gradeLevelCode": "G4",
    "gradeLevelName": "Grade 4",
    "sectionId": 7,
    "sectionCode": "A",
    "sectionName": "Section A",
    "gradeLevelSubjectId": 10,
    "subjectId": 11,
    "subjectCode": "ENG",
    "subjectName": "English",
    "subjectSheetId": 50,
    "status": "SUBMITTED",
    "sheetVersion": 2,
    "totalStudents": 30,
    "completedMarks": 30
  }
]
```

`status` is `NOT_CREATED` when no subject sheet exists yet; otherwise it is the
sheet status: `DRAFT`, `SUBMITTED`, `RETURNED`, or `APPROVED`.

## Subject Sheet

Create or retrieve the subject sheet for a scheduled assessment and section:

`POST /api/v1/results/academic-years/{academicYearId}/examinations/{examinationId}/assessments/{scheduledAssessmentId}/sections/{sectionId}/subject-sheet`

The roster is snapshotted from students eligible in the section on the
scheduled assessment date.

Get an existing subject sheet:

`GET /api/v1/results/subject-sheets/{subjectSheetId}`

Response body:

```json
{
  "id": 30,
  "academicYearId": 2,
  "examinationId": 3,
  "scheduledAssessmentId": 6,
  "gradeLevelId": 4,
  "sectionId": 7,
  "gradeLevelSubjectId": 5,
  "maximumMarks": 50.00,
  "assessmentDate": "2026-10-05",
  "status": "DRAFT",
  "submittedByUserId": null,
  "submittedAt": null,
  "reviewedByUserId": null,
  "reviewedAt": null,
  "reviewNote": null,
  "version": 0,
  "marks": [
    {
      "id": 31,
      "studentEnrollmentId": 11,
      "studentUserId": 21,
      "studentDisplayName": "Arun Das",
      "rollNumber": "A001",
      "absent": false,
      "marksObtained": null,
      "version": 0,
      "createdAt": "2026-10-05T09:00:00Z",
      "updatedAt": "2026-10-05T09:00:00Z"
    }
  ],
  "createdAt": "2026-10-05T09:00:00Z",
  "updatedAt": "2026-10-05T09:00:00Z"
}
```

## Mark Entry

Save all marks for a subject sheet:

`PUT /api/v1/results/subject-sheets/{subjectSheetId}/marks`

Request body:

```json
{
  "sheetVersion": 0,
  "marks": [
    {
      "studentEnrollmentId": 11,
      "absent": false,
      "marksObtained": 0.00,
      "version": 0
    },
    {
      "studentEnrollmentId": 12,
      "absent": true,
      "marksObtained": null,
      "version": 0
    }
  ]
}
```

Rules:

- The request must include every rostered student exactly once.
- `absent=true` requires `marksObtained=null`.
- `absent=false` requires `marksObtained` during save.
- A present student may receive `0.00`.
- Marks cannot exceed the scheduled assessment `maximumMarks`.
- `sheetVersion` and each mark `version` must match the current locked rows.

Submit a completed subject sheet:

`POST /api/v1/results/subject-sheets/{subjectSheetId}/submit`

Request body:

```json
{
  "version": 1
}
```

All marks must be complete before submission.

## Review

Return a submitted subject sheet:

`POST /api/v1/results/subject-sheets/{subjectSheetId}/return`

Request body:

```json
{
  "version": 2,
  "note": "Please verify absentee entry."
}
```

Approve a submitted subject sheet:

`POST /api/v1/results/subject-sheets/{subjectSheetId}/approve`

Request body:

```json
{
  "version": 2
}
```

Only `SUBMITTED` sheets can be returned or approved. Approved sheets are not
editable in this increment.

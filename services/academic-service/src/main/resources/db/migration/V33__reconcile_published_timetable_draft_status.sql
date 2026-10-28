-- A publication is immutable evidence that its source draft completed the workflow.
-- Reconcile legacy rows created before the source status was updated reliably.
UPDATE timetable_drafts draft
SET workflow_status = 'PUBLISHED',
    effective_from_intent = publication.effective_from,
    effective_to_exclusive_intent = publication.effective_to_exclusive,
    version = draft.version + 1,
    updated_at = GREATEST(draft.updated_at, publication.published_at)
FROM timetable_publications publication
WHERE publication.organization_id = draft.organization_id
  AND publication.academic_year_id = draft.academic_year_id
  AND publication.source_draft_id = draft.id
  AND draft.workflow_status <> 'PUBLISHED';

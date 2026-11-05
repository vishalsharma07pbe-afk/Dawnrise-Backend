ALTER TABLE staff_leave_requests DROP CONSTRAINT chk_staff_leave_request_status;
ALTER TABLE staff_leave_requests ADD CONSTRAINT chk_staff_leave_request_status CHECK(status IN
 ('DRAFT','PENDING','PENDING_CORRECTION','APPROVED','REJECTED','WITHDRAWN','CANCELLED','PARTIALLY_CANCELLED','DELETED'));

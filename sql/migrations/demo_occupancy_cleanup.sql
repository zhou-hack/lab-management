-- 演示占用数据清理：仅删除 purpose 带「教学演示占用」前缀的种子行及其关联
USE lab_management;
DELETE e FROM document_event e JOIN biz_document d ON e.document_id = d.id
  JOIN lab_booking b ON d.idempotency_key = CAST(b.id AS CHAR)
  WHERE b.purpose LIKE '教学演示占用%' AND b.applicant_id = 2;
DELETE t FROM approval_task t JOIN biz_document d ON t.document_id = d.id
  JOIN lab_booking b ON d.idempotency_key = CAST(b.id AS CHAR)
  WHERE b.purpose LIKE '教学演示占用%' AND b.applicant_id = 2;
DELETE d FROM biz_document d JOIN lab_booking b ON d.idempotency_key = CAST(b.id AS CHAR)
  WHERE b.purpose LIKE '教学演示占用%' AND b.applicant_id = 2;
DELETE s FROM lab_slot s JOIN lab_booking b ON s.booking_id = b.id
  WHERE b.purpose LIKE '教学演示占用%' AND b.applicant_id = 2;
DELETE FROM lab_booking WHERE purpose LIKE '教学演示占用%' AND applicant_id = 2;

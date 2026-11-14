-- SYNTHETIC USERS ONLY. This file is deliberately not standalone: run it only through
-- the boundary runner which hardcodes m6-course-mysql/course_integration.
INSERT INTO teacher(user_id,employee_no,name,creator,created_at,modifier,modi_at)
VALUES ('m6-teacher-leader','M6-T1','Synthetic Leader','m6',NOW(6),'m6',NOW(6)),
('m6-teacher-cc','M6-T2','Synthetic CC Teacher','m6',NOW(6),'m6',NOW(6)),
('m6-teacher-no-cc','M6-T3','Synthetic No CC','m6',NOW(6),'m6',NOW(6))
ON DUPLICATE KEY UPDATE deleted_at=NULL;
INSERT INTO term(id,name,starts_on,ends_on,is_current,creator,created_at,modifier,modi_at)
VALUES(610001,'M6 fixture term','2026-01-01','2036-12-31',FALSE,'m6',NOW(6),'m6',NOW(6));
INSERT INTO course(id,term_id,code,name,description,work_mode,status,creator,created_at,modifier,modi_at)
VALUES(610001,610001,'M6-FIXTURE','M6 fixture course','','group',3,'m6',NOW(6),'m6',NOW(6));
INSERT INTO course_leader_assignment(course_id,teacher_id,creator,created_at,modifier,modi_at)
SELECT 610001,id,'m6',NOW(6),'m6',NOW(6) FROM teacher WHERE user_id='m6-teacher-leader';
INSERT INTO teaching_class(id,course_id,no,name,creator,created_at,modifier,modi_at)
VALUES(610001,610001,1,'M6 fixture class','m6',NOW(6),'m6',NOW(6));
INSERT INTO class_teacher_assignment(class_id,teacher_id,creator,created_at,modifier,modi_at)
SELECT 610001,id,'m6',NOW(6),'m6',NOW(6) FROM teacher WHERE user_id='m6-teacher-leader';
INSERT INTO student(id,user_id,student_no,name,status,creator,created_at,modifier,modi_at)
VALUES(610001,'m6-student-leader','M6-S1','Synthetic Student Leader','active','m6',NOW(6),'m6',NOW(6)),
(610002,'m6-student-applicant','M6-S2','Synthetic Student Applicant','active','m6',NOW(6),'m6',NOW(6));
INSERT INTO enrollment(class_id,student_id,course_id,source,creator,created_at,modifier,modi_at)
VALUES(610001,610001,610001,'manual','m6',NOW(6),'m6',NOW(6)),(610001,610002,610001,'manual','m6',NOW(6),'m6',NOW(6));
INSERT INTO course_grouping_rule(course_id,min_size,max_size,allow_cross_class,starts_at,formation_closes_at,creator,created_at,modifier,modi_at)
VALUES(610001,1,5,FALSE,'2020-01-01','2036-12-31','m6',NOW(6),'m6',NOW(6));
INSERT INTO project_group(id,course_id,responsible_class_id,group_no,name,creator,created_at,modifier,modi_at)
VALUES(610001,610001,610001,'M6-G1','Synthetic Group','m6',NOW(6),'m6',NOW(6));
INSERT INTO group_membership(group_id,course_id,student_id,is_leader,creator,created_at,modifier,modi_at)
VALUES(610001,610001,610001,TRUE,'m6',NOW(6),'m6',NOW(6));
INSERT INTO group_join_request(id,group_id,course_id,student_id,state,pending_marker,creator,created_at,modifier,modi_at)
VALUES(610001,610001,610001,610002,1,1,'m6',NOW(6),'m6',NOW(6));
INSERT INTO milestone(id,course_id,name,description,due_at,mode,weight,scale,group_ratio,done,creator,created_at,modifier,modi_at)
VALUES(610001,610001,'M6 Oral','','2036-01-01','oral',100,100,50,FALSE,'m6',NOW(6),'m6',NOW(6));
INSERT INTO oral_assessment(id,milestone_id,subject_type,group_id,subject_no,subject_name,verdict,group_score,assessed_by_teacher_id,creator,created_at,modifier,modi_at)
SELECT 610001,610001,'group',610001,'M6-G1','Synthetic Group','passed',70,id,'m6',NOW(6),'m6',NOW(6) FROM teacher WHERE user_id='m6-teacher-leader';
INSERT INTO oral_individual_score(assessment_id,student_id,score,creator,created_at,modifier,modi_at)
VALUES(610001,610001,70,'m6',NOW(6),'m6',NOW(6));
-- Assert subject mapping with actual business records, not JWT name claims.
SELECT user_id,id FROM teacher WHERE user_id LIKE 'm6-%';
SELECT user_id,id FROM student WHERE user_id LIKE 'm6-%';

-- MySQL dump 10.13  Distrib 5.7.44, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: linux_judge_system
-- ------------------------------------------------------
-- Server version	5.7.44-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `attachment`
--

DROP TABLE IF EXISTS `attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `ownerType` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Owner type: question/question_answer/sub_question/sub_question_answer/submission',
  `ownerId` bigint(20) NOT NULL COMMENT 'Owner object id',
  `storageKey` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Object storage path for the image',
  `mimeType` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'File type, e.g. image/png',
  `fileSize` bigint(20) DEFAULT NULL COMMENT 'File size in bytes',
  `orderIndex` int(11) NOT NULL DEFAULT '0' COMMENT 'Display order under the same owner',
  `uploaderId` bigint(20) NOT NULL COMMENT 'Uploader userId',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`ownerType`,`ownerId`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Attachment table (metadata for images and other files; actual files are stored in S3)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `class`
--

DROP TABLE IF EXISTS `class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Class name, e.g. CS Class 3 of 2022',
  `teacherId` bigint(20) NOT NULL COMMENT 'Teacher userId',
  `semester` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Semester, e.g. Spring 2026',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_teacher_name_semester` (`teacherId`,`name`,`semester`,`isDelete`),
  KEY `idx_teacher` (`teacherId`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Class table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `class_student`
--

DROP TABLE IF EXISTS `class_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `class_student` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `classId` bigint(20) NOT NULL COMMENT 'Class id',
  `studentId` bigint(20) NOT NULL COMMENT 'Student userId',
  `joinTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time joined the class',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_student` (`classId`,`studentId`,`isDelete`),
  KEY `idx_class` (`classId`),
  KEY `idx_student` (`studentId`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Class-student association table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dispute`
--

DROP TABLE IF EXISTS `dispute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `dispute` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `submissionId` bigint(20) NOT NULL COMMENT 'Related submission.id',
  `disputeReason` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Student dispute reason for this round',
  `disputeTime` datetime NOT NULL COMMENT 'Dispute submission time for this round',
  `manualComment` text COLLATE utf8mb4_unicode_ci COMMENT 'Teacher handling comment for this round; null if not yet handled',
  `resolveTime` datetime DEFAULT NULL COMMENT 'Time this round was resolved; null if not yet handled',
  `withdrawnTime` datetime DEFAULT NULL COMMENT 'Time the student withdrew this round; null if not withdrawn',
  `replyReadTime` datetime DEFAULT NULL COMMENT 'Time the student read the teacher handling for this round; null if unread',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  KEY `idx_dispute_submission_time` (`submissionId`,`disputeTime`),
  CONSTRAINT `fk_dispute_submission` FOREIGN KEY (`submissionId`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='submission dispute round history';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `experiment`
--

DROP TABLE IF EXISTS `experiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `experiment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Experiment title',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT 'Experiment description / submission requirements',
  `classId` bigint(20) DEFAULT NULL COMMENT 'Published class id',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Status: 0-draft, 1-published, 2-closed, 3-unpublished',
  `userId` bigint(20) NOT NULL COMMENT 'Creator user id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `deadlineTime` datetime DEFAULT NULL COMMENT 'Deadline',
  `lastPublishedTime` datetime DEFAULT NULL COMMENT 'Time of the most recent transition from draft to published; updated only on 0→1 status change; used to determine which publish round a student submission belongs to',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Experiment table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `experiment_question`
--

DROP TABLE IF EXISTS `experiment_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `experiment_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `experimentId` bigint(20) NOT NULL COMMENT 'Experiment id',
  `questionId` bigint(20) NOT NULL COMMENT 'Question id',
  `points` int(11) NOT NULL DEFAULT '0' COMMENT 'Points for this question in this experiment (0 means use question.totalScore)',
  `sortOrder` int(11) NOT NULL DEFAULT '0' COMMENT 'Display order of the question in the experiment',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  KEY `idx_experiment` (`experimentId`),
  KEY `idx_question` (`questionId`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Experiment-question association table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question`
--

DROP TABLE IF EXISTS `question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Title',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT 'Overall question description (Markdown; images are referenced via the attachment table, not stored directly)',
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Question type: shell_script/c_program/qa',
  `difficulty` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Difficulty: easy/medium/hard',
  `tags` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Knowledge-point tags (JSON array or comma-separated)',
  `totalScore` int(11) NOT NULL DEFAULT '0' COMMENT 'Redundant field: sum of sub-question scores, so the list page can display totals without join+sum',
  `submitNum` int(11) NOT NULL DEFAULT '0' COMMENT 'Number of submissions for this question',
  `userId` bigint(20) NOT NULL COMMENT 'Creator user id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Question';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sub_question`
--

DROP TABLE IF EXISTS `sub_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sub_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `questionId` bigint(20) NOT NULL COMMENT 'Parent question id',
  `orderIndex` int(11) NOT NULL DEFAULT '0' COMMENT 'Display/grading order within the question',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT 'Specific requirements of this sub-question',
  `answerFormat` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Required answer format: image/text/code',
  `language` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Language for code sub-questions, e.g. bash/c; used only when answerFormat=code; determines which sandbox image is used for grading',
  `referenceAnswer` text COLLATE utf8mb4_unicode_ci COMMENT 'Reference answer for this sub-question',
  `rubric` text COLLATE utf8mb4_unicode_ci COMMENT 'Grading rubric for this sub-question',
  `allowSandbox` tinyint(4) NOT NULL DEFAULT '1' COMMENT 'Whether AI is allowed to call the sandbox when needed',
  `score` int(11) NOT NULL DEFAULT '10' COMMENT 'Score of this sub-question',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  KEY `idx_question` (`questionId`)
) ENGINE=InnoDB AUTO_INCREMENT=2820 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sub-question';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sub_question_score`
--

DROP TABLE IF EXISTS `sub_question_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sub_question_score` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `submissionId` bigint(20) NOT NULL COMMENT 'Related student submission id',
  `subQuestionId` bigint(20) NOT NULL COMMENT 'Related sub-question id',
  `aiScore` decimal(5,2) DEFAULT NULL COMMENT 'Raw AI score',
  `manualScore` decimal(5,2) DEFAULT NULL COMMENT 'Teacher manual score override (if non-null, overrides aiScore)',
  `effectiveScore` decimal(5,2) DEFAULT NULL COMMENT 'Effective score = manualScore ?? aiScore; stored redundantly for stats queries',
  `usedSandbox` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether AI actually invoked the sandbox during this grading, 0=no 1=yes',
  `aiEvidence` text COLLATE utf8mb4_unicode_ci COMMENT 'AI scoring evidence / comments',
  `gradingLog` text COLLATE utf8mb4_unicode_ci COMMENT 'Grading process log (including whether the sandbox was called and why, sandbox output, etc.), for dispute review',
  `gradedAt` datetime DEFAULT NULL COMMENT 'Time this sub-question grading was completed',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_subquestion` (`submissionId`,`subQuestionId`),
  KEY `idx_submission` (`submissionId`),
  KEY `idx_sub_question` (`subQuestionId`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sub-question score result table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `submission`
--

DROP TABLE IF EXISTS `submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `experimentQuestionId` bigint(20) NOT NULL COMMENT 'Related experiment-question id',
  `studentId` bigint(20) NOT NULL COMMENT 'Student userId',
  `answerBlocks` json DEFAULT NULL COMMENT 'Answer content: ordered text/code/image blocks (grouped by sub-question; see JSON structure notes below)',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Grading status: -1 draft, 0 pending, 1 grading, 2 completed, 3 grading failed',
  `aiScore` int(11) DEFAULT NULL COMMENT 'Raw AI score (= sum of AI scores of all sub-questions)',
  `score` int(11) DEFAULT NULL COMMENT 'Final effective score (visible to students, = sum of sub-question effectiveScore)',
  `aiEvidence` text COLLATE utf8mb4_unicode_ci COMMENT 'Overall AI scoring evidence/rationale (see sub_question_score.aiEvidence for sub-question-level details)',
  `isManualOverride` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether locked by teacher manual override (question-level change; see sub_question_score for sub-question-level changes)',
  `version` int(11) NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version number',
  `submitTime` datetime DEFAULT NULL COMMENT 'Confirmed submit time; empty while in draft',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  `draftLock` tinyint(4) GENERATED ALWAYS AS ((case when (`status` = -(1)) then 1 else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_active_draft` (`experimentQuestionId`,`studentId`,`draftLock`),
  KEY `idx_experiment_question` (`experimentQuestionId`),
  KEY `idx_student` (`studentId`),
  KEY `idx_experiment_question_student_time` (`experimentQuestionId`,`studentId`,`submitTime`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='student experiment submission';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userAccount` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Account',
  `userPassword` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Password',
  `userName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User nickname',
  `userAvatar` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User avatar',
  `userProfile` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User profile',
  `userEmail` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'user email address',
  `userRole` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT 'User role: user/admin/ban',
  `userType` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Business identity: teacher/student; only valid when userRole=user; leave empty for admin rows',
  `major` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Major; only valid for students',
  `createdBy` bigint(20) DEFAULT NULL COMMENT 'Creator id: teacher imported by admin, or student imported by teacher; no foreign key constraint, same style as classId/studentId',
  `tokenVersion` int(11) NOT NULL DEFAULT '0' COMMENT 'JWT version; incremented on logout / password change / password reset',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  `isDelete` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Whether deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_userAccount` (`userAccount`),
  KEY `idx_user_type` (`userType`),
  KEY `idx_created_by` (`createdBy`)
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='user';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-26 15:16:03

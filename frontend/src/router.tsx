import { createBrowserRouter, Navigate } from "react-router-dom"

import { ProtectedRoute } from "@/components/ProtectedRoute"
import { StudentLayout } from "@/components/layout/StudentLayout"
import { TeacherLayout } from "@/components/layout/TeacherLayout"
import { Login } from "@/pages/Login"
import { Analytics } from "@/pages/analytics/Analytics"
import { Dashboard } from "@/pages/Dashboard"
import { Roster } from "@/pages/roster/Roster"
import { Settings } from "@/pages/Settings"
import { AssignmentCreate } from "@/pages/assignments/AssignmentCreate"
import { AssignmentEdit } from "@/pages/assignments/AssignmentEdit"
import { AssignmentList } from "@/pages/assignments/AssignmentList"
import { SubmissionList as TeacherAssignmentSubmissionList } from "@/pages/assignments/SubmissionList"
import { SubmissionList as TeacherSubmissionHistoryList } from "@/pages/submissions/SubmissionList"
import { DisputeList as TeacherDisputeList } from "@/pages/disputes/DisputeList"
import { QuestionCreate } from "@/pages/questions/QuestionCreate"
import { QuestionEdit } from "@/pages/questions/QuestionEdit"
import { QuestionList } from "@/pages/questions/QuestionList"
import { QuestionPreview } from "@/pages/questions/QuestionPreview"
import { QuestionPreviewById } from "@/pages/questions/QuestionPreviewById"
import { SubmissionDetail as TeacherSubmissionDetail } from "@/pages/submissions/SubmissionDetail"
import { StudentDashboard } from "@/pages/student/StudentDashboard"
import { Settings as StudentSettings } from "@/pages/student/Settings"
import { AssignmentDetail } from "@/pages/student/assignments/AssignmentDetail"
import { AssignmentList as StudentAssignmentList } from "@/pages/student/assignments/AssignmentList"
import { DisputeList as StudentDisputeList } from "@/pages/student/disputes/DisputeList"
import { SubmissionDetail as StudentSubmissionDetail } from "@/pages/student/submissions/SubmissionDetail"
import { SubmissionList as StudentSubmissionList } from "@/pages/student/submissions/SubmissionList"

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/teacher" replace />,
  },
  {
    path: "/login",
    element: <Login />,
  },
  {
    path: "/teacher",
    element: (
      <ProtectedRoute requiredUserType="teacher">
        <TeacherLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <Dashboard />,
        handle: { titleKey: "nav:home" },
      },
      {
        path: "questions",
        element: <QuestionList />,
        handle: { titleKey: "nav:questions" },
      },
      {
        path: "questions/new",
        element: <QuestionCreate />,
        handle: { titleKey: "nav:questionCreate" },
      },
      {
        path: "questions/preview",
        element: <QuestionPreview />,
        handle: { titleKey: "nav:questionPreview" },
      },
      {
        path: "questions/:qid/preview",
        element: <QuestionPreviewById />,
        handle: { titleKey: "nav:questionPreview" },
      },
      {
        path: "questions/:qid/edit",
        element: <QuestionEdit />,
        handle: { titleKey: "nav:questionEdit" },
      },
      {
        path: "assignments",
        element: <AssignmentList />,
        handle: { titleKey: "nav:assignments" },
      },
      {
        path: "assignments/new",
        element: <AssignmentCreate />,
        handle: { titleKey: "nav:assignmentCreate" },
      },
      {
        path: "assignments/:id/edit",
        element: <AssignmentEdit />,
        handle: { titleKey: "nav:assignmentEdit" },
      },
      {
        path: "assignments/:id/submissions",
        element: <TeacherAssignmentSubmissionList />,
        handle: { titleKey: "nav:submissionList" },
      },
      {
        path: "submissions",
        element: <TeacherSubmissionHistoryList />,
        handle: { titleKey: "nav:submissions" },
      },
      {
        path: "submissions/:experimentId/students/:studentId",
        element: <TeacherSubmissionDetail />,
        handle: { titleKey: "nav:submissionDetail" },
      },
      {
        path: "disputes",
        element: <TeacherDisputeList />,
        handle: { titleKey: "nav:disputes" },
      },
      {
        path: "analytics",
        element: <Analytics />,
        handle: { titleKey: "nav:grades" },
      },
      {
        path: "roster",
        element: <Roster />,
        handle: { titleKey: "nav:roster" },
      },
      {
        path: "settings",
        element: <Settings />,
        handle: { titleKey: "nav:settings" },
      },
    ],
  },
  {
    path: "/student",
    element: (
      <ProtectedRoute requiredUserType="student">
        <StudentLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <StudentDashboard />,
        handle: { titleKey: "nav:home" },
      },
      {
        path: "assignments",
        element: <StudentAssignmentList />,
        handle: { titleKey: "nav:assignments" },
      },
      {
        path: "assignments/:id",
        element: <AssignmentDetail />,
        handle: { titleKey: "nav:assignmentDetail" },
      },
      {
        path: "submissions",
        element: <StudentSubmissionList />,
        handle: { titleKey: "nav:submissions" },
      },
      {
        path: "submissions/:id",
        element: <StudentSubmissionDetail />,
        handle: { titleKey: "nav:submissionDetail" },
      },
      {
        path: "disputes",
        element: <StudentDisputeList />,
        handle: { titleKey: "nav:myDisputes" },
      },
      {
        path: "settings",
        element: <StudentSettings />,
        handle: { titleKey: "nav:settings" },
      },
    ],
  },
])

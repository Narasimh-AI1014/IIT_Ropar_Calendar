import { Routes, Route, Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import AdminLayout from "../layouts/AdminLayout";
import Login from "../pages/Login";
import SmartImport from "../pages/SmartImport";
import CampusCalendar from "../pages/CampusCalendar";
import CreateEvent from "../pages/CreateEvent";

function ProtectedRoute() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Outlet />;
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AdminLayout />}>
          <Route path="/" element={<Navigate to="/campus" replace />} />
          <Route path="/import" element={<SmartImport />} />
          <Route path="/campus" element={<CampusCalendar />} />
          <Route path="/events/new" element={<CreateEvent />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
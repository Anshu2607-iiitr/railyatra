import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthProvider';

export function ProtectedRoute({ requiredRole }: { requiredRole?: string }) {
  const { user, loading } = useAuth();
  if (loading) return <section className="page-message">Restoring your secure session…</section>;
  if (!user) return <Navigate to="/login" replace />;
  if (requiredRole && user.role !== requiredRole) return <Navigate to="/" replace />;
  return <Outlet />;
}

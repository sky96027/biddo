import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

const PrivateRoute = ({ children }: { children: React.ReactNode }) => {
  const token = useAuthStore((s) => s.accessToken)
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default PrivateRoute
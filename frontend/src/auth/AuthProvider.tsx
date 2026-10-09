import axios from 'axios';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type AuthUser = { id: number; fullName: string; email: string; phone: string; role: string };
type AuthResponse = { accessToken: string; expiresInSeconds: number; user: AuthUser };
type RegisterPayload = { fullName: string; email: string; phone: string; password: string; confirmPassword: string };
type AuthContextValue = {
  user: AuthUser | null; accessToken: string | null; loading: boolean;
  login: (identifier: string, password: string) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ apiBaseUrl, children }: { apiBaseUrl: string; children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const accept = (response: AuthResponse) => { setUser(response.user); setAccessToken(response.accessToken); };
  useEffect(() => {
    axios.post<AuthResponse>(`${apiBaseUrl}/api/auth/refresh`, {}, { withCredentials: true })
      .then(response => accept(response.data)).catch(() => { setUser(null); setAccessToken(null); }).finally(() => setLoading(false));
  }, [apiBaseUrl]);
  const login = useCallback(async (identifier: string, password: string) => {
    const response = await axios.post<AuthResponse>(`${apiBaseUrl}/api/auth/login`, { identifier, password }, { withCredentials: true });
    accept(response.data);
  }, [apiBaseUrl]);
  const register = useCallback(async (payload: RegisterPayload) => {
    const response = await axios.post<AuthResponse>(`${apiBaseUrl}/api/auth/register`, payload, { withCredentials: true });
    accept(response.data);
  }, [apiBaseUrl]);
  const logout = useCallback(async () => {
    try { await axios.post(`${apiBaseUrl}/api/auth/logout`, {}, { withCredentials: true }); }
    finally { setUser(null); setAccessToken(null); }
  }, [apiBaseUrl]);
  const value = useMemo(() => ({ user, accessToken, loading, login, register, logout }), [user, accessToken, loading, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}

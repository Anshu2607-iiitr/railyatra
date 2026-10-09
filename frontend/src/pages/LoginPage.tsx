import axios from 'axios';
import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

export function LoginPage() {
  const { login } = useAuth(); const navigate = useNavigate(); const location = useLocation();
  const [identifier, setIdentifier] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [submitting, setSubmitting] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setSubmitting(true);
    try { await login(identifier, password); navigate((location.state as { from?: string } | null)?.from || '/account', { replace: true }); }
    catch (reason) { setError(axios.isAxiosError(reason) ? String(reason.response?.data?.message || 'Unable to sign in') : 'Unable to sign in'); }
    finally { setSubmitting(false); }
  }
  return <section className="auth-page"><div className="auth-card"><p className="eyebrow">RAILYATRA ACCOUNT</p><h1>Welcome back</h1><p className="auth-intro">Sign in to manage your RailYatra journeys.</p>
    <form onSubmit={submit} className="auth-form"><label>Email or phone<input value={identifier} onChange={e => setIdentifier(e.target.value)} autoComplete="username" required /></label><label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="primary-search" disabled={submitting}>{submitting ? 'Signing in…' : 'Login'}</button></form>
    <p className="auth-switch">New to RailYatra? <Link to="/register">Create an account</Link></p></div></section>;
}

import axios from 'axios';
import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';

export function RegisterPage() {
  const { register } = useAuth(); const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '', confirmPassword: '' }); const [error, setError] = useState(''); const [submitting, setSubmitting] = useState(false);
  const change = (field: keyof typeof form, value: string) => setForm(current => ({ ...current, [field]: value }));
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (form.password !== form.confirmPassword) { setError('Passwords do not match'); return; }
    setSubmitting(true);
    try { await register(form); navigate('/account', { replace: true }); }
    catch (reason) { setError(axios.isAxiosError(reason) ? String(reason.response?.data?.message || 'Unable to create account') : 'Unable to create account'); }
    finally { setSubmitting(false); }
  }
  return <section className="auth-page"><div className="auth-card"><p className="eyebrow">RAILYATRA ACCOUNT</p><h1>Create your account</h1><p className="auth-intro">One account for your upcoming RailYatra bookings.</p>
    <form onSubmit={submit} className="auth-form"><label>Full name<input value={form.fullName} onChange={e => change('fullName', e.target.value)} autoComplete="name" maxLength={140} required /></label><label>Email<input type="email" value={form.email} onChange={e => change('email', e.target.value)} autoComplete="email" required /></label><label>Phone<input value={form.phone} onChange={e => change('phone', e.target.value)} autoComplete="tel" required /></label><label>Password <small>At least 8 characters</small><input type="password" value={form.password} onChange={e => change('password', e.target.value)} autoComplete="new-password" minLength={8} required /></label><label>Confirm password<input type="password" value={form.confirmPassword} onChange={e => change('confirmPassword', e.target.value)} autoComplete="new-password" required /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="primary-search" disabled={submitting}>{submitting ? 'Creating account…' : 'Create account'}</button></form>
    <p className="auth-switch">Already have an account? <Link to="/login">Login</Link></p></div></section>;
}

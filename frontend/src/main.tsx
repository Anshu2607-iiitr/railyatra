import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { CircleCheck, CircleX, Menu, TrainFront } from 'lucide-react';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AccountPage } from './pages/AccountPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { PassengerBookingPage } from './pages/PassengerBookingPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { PnrStatusPage } from './pages/PnrStatusPage';
import { BookingConfirmationPage } from './pages/BookingConfirmationPage';
import { CancellationPage } from './pages/CancellationPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AdminTrainsPage } from './pages/AdminTrainsPage';
import { TrainSearchResultsPage } from './pages/TrainSearchResultsPage';
import './styles.css';

type HealthResponse = { status: string; application: string };
const apiBase = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

function Layout() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  useEffect(() => { axios.get<HealthResponse>(`${apiBase}/api/health`).then(r => setHealth(r.data)).catch(() => setError('Backend unavailable')); }, []);
  async function signOut() { await logout(); setMenuOpen(false); navigate('/'); }
  return <main><header className="navbar"><Link to="/" className="brand"><TrainFront size={27}/><span>RailYatra</span></Link>
    <nav><Link to="/trains">Trains</Link><Link to="/pnr">PNR Status</Link><Link to="/my-bookings">My Bookings</Link><Link to="/my-bookings">Cancel Ticket</Link><span className="nav-disabled">Help</span></nav>
    <div className="account">{user ? <>{user.role === 'ROLE_ADMIN' && <Link to="/admin">Admin</Link>}<Link to="/account" className="account-name">{user.fullName.split(' ')[0]}</Link><button onClick={signOut}>Logout</button></> : <><Link to="/login">Login</Link><Link to="/register" className="register">Register</Link></>}</div><button className="mobile-menu" aria-label={menuOpen ? 'Close menu' : 'Open menu'} aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={() => setMenuOpen(open => !open)}><Menu/></button></header>
    {menuOpen && <nav id="mobile-navigation" className="mobile-navigation" onClick={() => setMenuOpen(false)} aria-label="Mobile navigation">
      <Link to="/">Home</Link><Link to="/trains">Trains</Link><Link to="/pnr">PNR Status</Link><Link to="/my-bookings">My Bookings / Cancel Ticket</Link>
      {user ? <>{user.role === 'ROLE_ADMIN' && <Link to="/admin">Admin Dashboard</Link>}<Link to="/account">My Account</Link><button onClick={signOut}>Logout</button></> : <><Link to="/login">Login</Link><Link to="/register">Register</Link></>}
    </nav>}
    <Routes>
      <Route path="/" element={<HomePage apiBaseUrl={apiBase}/>}/>
      <Route path="/trains" element={<TrainSearchResultsPage apiBaseUrl={apiBase}/>}/>
      <Route path="/pnr" element={<PnrStatusPage apiBaseUrl={apiBase}/>}/>
      <Route path="/login" element={<LoginPage/>}/><Route path="/register" element={<RegisterPage/>}/>
      <Route element={<ProtectedRoute requiredRole="ROLE_ADMIN"/>}><Route path="/admin" element={<AdminDashboardPage apiBaseUrl={apiBase}/>}/><Route path="/admin/trains" element={<AdminTrainsPage apiBaseUrl={apiBase}/>}/></Route>
      <Route element={<ProtectedRoute/>}>
        <Route path="/account" element={<AccountPage apiBaseUrl={apiBase}/>}/><Route path="/my-bookings" element={<AccountPage apiBaseUrl={apiBase}/>}/>
        <Route path="/book" element={<PassengerBookingPage apiBaseUrl={apiBase}/>}/><Route path="/checkout" element={<CheckoutPage apiBaseUrl={apiBase}/>}/>
        <Route path="/bookings/:reference/confirmation" element={<BookingConfirmationPage apiBaseUrl={apiBase}/>}/>
        <Route path="/bookings/:reference/cancel" element={<CancellationPage apiBaseUrl={apiBase}/>}/>
      </Route>
    </Routes>
    <section className="api-status">{health ? <><CircleCheck size={15}/>{health.application} {health.status}</> : <><CircleX size={15}/>{error || 'Checking API'}</>}</section><footer>RailYatra is an independent platform and is not affiliated with Indian Railways or IRCTC.</footer></main>;
}

createRoot(document.getElementById('root')!).render(<BrowserRouter><AuthProvider apiBaseUrl={apiBase}><Layout/></AuthProvider></BrowserRouter>);

import { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import { useAuth } from '../auth/AuthProvider';
import { connectAdminDashboard } from '../realtime/adminDashboardStream';
import type { ConnectionState } from '../realtime/bookingStream';
import './admin-dashboard.css';

type Dashboard = {
  businessDate: string; timeZone: string; totalTrains: number; stations: number;
  bookingsToday: number; confirmed: number; rac: number; waitingList: number; cancellations: number;
  revenue: number; grossRevenue: number; completedRefunds: number; activeSeatHolds: number;
};
const integer = new Intl.NumberFormat('en-IN');
const currency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const connectionLabels: Record<ConnectionState, string> = {
  offline: 'Live updates offline', connecting: 'Connecting live updates…', live: 'Live · watching database changes',
  reconnecting: 'Reconnecting · automatic refresh continues', unavailable: 'Live access unavailable · sign in again',
};

export function AdminDashboardPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const { accessToken, user } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [revision, setRevision] = useState(0);
  const [connection, setConnection] = useState<ConnectionState>('connecting');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [fetchedAt, setFetchedAt] = useState<Date | null>(null);

  useEffect(() => {
    if (!accessToken || user?.role !== 'ROLE_ADMIN') { setData(null); setConnection('unavailable'); return; }
    let active = true;
    const stop = connectAdminDashboard(`${apiBaseUrl}/api/admin/dashboard/events`, accessToken,
      () => { if (active) setRevision(value => value + 1); },
      state => { if (active) setConnection(state); });
    return () => { active = false; stop(); };
  }, [apiBaseUrl, accessToken, user?.role]);

  useEffect(() => {
    if (!accessToken || user?.role !== 'ROLE_ADMIN') return;
    const controller = new AbortController();
    setLoading(true);
    axios.get<Dashboard>(`${apiBaseUrl}/api/admin/dashboard`, {
      headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal,
    }).then(response => {
      if (controller.signal.aborted) return;
      setData(response.data); setError(''); setFetchedAt(new Date());
    }).catch(reason => {
      if (controller.signal.aborted) return;
      if (axios.isAxiosError(reason) && [401, 403].includes(reason.response?.status ?? 0)) {
        setData(null); setError('Administrator access is required. Sign in with an authorized account.');
      } else setError('Dashboard could not be refreshed. Previously fetched values may be stale.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [apiBaseUrl, accessToken, user?.role, revision]);

  // A slow fallback keeps the page useful when proxies/network block streaming.
  useEffect(() => {
    if (connection === 'live' || connection === 'unavailable') return;
    const timer = window.setInterval(() => setRevision(value => value + 1), 30_000);
    return () => window.clearInterval(timer);
  }, [connection]);

  const metrics = data ? [
    { label: 'Total Trains', value: integer.format(data.totalTrains), detail: 'Imported train records' },
    { label: 'Stations', value: integer.format(data.stations), detail: 'Stations in the railway database' },
    { label: 'Bookings Today', value: integer.format(data.bookingsToday), detail: `${data.businessDate} · ${data.timeZone}` },
    { label: 'Confirmed', value: integer.format(data.confirmed), detail: 'Current CNF bookings · all journeys' },
    { label: 'RAC', value: integer.format(data.rac), detail: 'Current RAC bookings · all journeys' },
    { label: 'Waiting List', value: integer.format(data.waitingList), detail: 'Current WL bookings · all journeys' },
    { label: 'Cancellations', value: integer.format(data.cancellations), detail: 'Completed full / partial cancellations' },
    { label: 'Revenue', value: currency.format(data.revenue), detail: 'Lifetime receipts minus completed refunds' },
    { label: 'Active Seat Holds', value: integer.format(data.activeSeatHolds), detail: 'Unconsumed holds that have not expired' },
  ] : [];

  return <section className="admin-dashboard" aria-labelledby="admin-title">
    <div className="admin-heading"><div><p className="admin-eyebrow">RailYatra administration</p><h1 id="admin-title">Operations overview</h1><p>Reservation activity and railway data, directly from the database.</p></div>
      <button className="admin-refresh" onClick={() => setRevision(value => value + 1)} disabled={loading}><RefreshCw size={16} className={loading ? 'admin-refreshing' : ''}/>{loading ? 'Refreshing…' : 'Refresh'}</button>
    </div>
    <Link to="/admin/trains" className="admin-management-link">Manage trains →</Link>{' · '}<Link to="/admin/inventory" className="admin-management-link">Manage seat inventory →</Link>
    <div className="admin-connection" role="status"><span className={connection === 'live' ? 'admin-live-dot' : 'admin-offline-dot'}/>{connectionLabels[connection]}{fetchedAt && <span className="admin-fetched">Last fetched {fetchedAt.toLocaleTimeString('en-IN')}</span>}</div>
    {error && <p className="admin-error" role="alert">{error}</p>}
    <div className="admin-grid" aria-busy={loading}>
      {!data && loading ? Array.from({ length: 9 }, (_, index) => <div key={index} className="admin-stat admin-skeleton" aria-hidden="true"><span/><strong/><span/></div>) : metrics.map(metric =>
        <article className="admin-stat" key={metric.label}><h2>{metric.label}</h2><strong>{metric.value}</strong><p>{metric.detail}</p></article>)}
    </div>
    {data && <div className="admin-ledger"><h2>Revenue reconciliation</h2><dl><div><dt>Successful receipts</dt><dd>{currency.format(data.grossRevenue)}</dd></div><div><dt>Completed refunds</dt><dd>{currency.format(data.completedRefunds)}</dd></div><div><dt>Net revenue</dt><dd>{currency.format(data.revenue)}</dd></div></dl><p>Failed and pending payments are excluded. Pending refunds reduce revenue after successful settlement.</p></div>}
    <p className="admin-note">Development railway records and simulated payments are internal RailYatra data, not official Indian Railways figures.</p>
  </section>;
}

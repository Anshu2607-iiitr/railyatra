import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import './my-bookings.css';
import { useBookingRealtime } from '../realtime/useBookingRealtime';
type Tab = 'UPCOMING' | 'COMPLETED' | 'CANCELLED';
type Booking = { internalBookingReference: string; currentStatus: string; currentStatusPosition: number | null; trainNumber: string; trainName: string; origin: string; destination: string; journeyDate: string; classCode: string; passengerCount: number; totalFare: number; providerType: string; canCancel: boolean };
type History = { content: Booking[]; page: number; size: number; totalElements: number; totalPages: number };
const tabs: Tab[] = ['UPCOMING', 'COMPLETED', 'CANCELLED'];
export function AccountPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const { user, accessToken } = useAuth();
  const { revision } = useBookingRealtime(apiBaseUrl, '*');
  const [tab, setTab] = useState<Tab>('UPCOMING'); const [page, setPage] = useState(0);
  const [history, setHistory] = useState<History | null>(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(''); const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setHistory(null);
    axios.get<History>(`${apiBaseUrl}/api/bookings/mine`, { params: { tab, page, size: 10 }, headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal })
      .then(response => { setHistory(response.data); if (page > 0 && response.data.content.length === 0) setPage(Math.max(0, response.data.totalPages - 1)); })
      .catch(reason => { if (!controller.signal.aborted) setError(axios.isAxiosError(reason) && reason.response?.status === 401 ? 'Your session has expired. Please sign in again.' : 'Could not load your bookings. Please retry.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [apiBaseUrl, accessToken, tab, page, reload, revision]);
  async function download(booking: Booking) {
    setBusy(booking.internalBookingReference); setActionError('');
    try {
      const response = await axios.get(`${apiBaseUrl}/api/bookings/${encodeURIComponent(booking.internalBookingReference)}/ticket.pdf`, { headers: { Authorization: `Bearer ${accessToken}` }, responseType: 'blob' });
      const url = URL.createObjectURL(response.data); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `RailYatra-${booking.internalBookingReference}.pdf`; document.body.appendChild(anchor); anchor.click(); anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setActionError('Could not download this ticket. Please try again.'); }
    finally { setBusy(''); }
  }
  return <section className="my-bookings"><p className="eyebrow">YOUR RAILYATRA ACCOUNT</p><h1>My Bookings</h1><p>Hello, {user?.fullName}. Manage your saved reservations here.</p>
    <div className="booking-tabs" role="tablist" aria-label="Booking history">{tabs.map(value => <button key={value} role="tab" id={`tab-${value}`} aria-controls="bookings-panel" aria-selected={tab === value} onClick={() => { setTab(value); setPage(0); setActionError(''); }} disabled={!!busy}>{value[0] + value.slice(1).toLowerCase()}</button>)}</div>
    {actionError && <p role="alert" className="form-error">{actionError}</p>}
    <div id="bookings-panel" role="tabpanel" aria-labelledby={`tab-${tab}`} aria-busy={loading}>
      {loading && <p role="status">Loading your bookings…</p>}
      {error && <div role="alert"><p className="form-error">{error}</p><button onClick={() => setReload(value => value + 1)}>Retry</button></div>}
      {!loading && !error && history?.content.length === 0 && <div className="booking-empty"><h2>No {tab.toLowerCase()} bookings</h2><p>Your reservations will appear here.</p><Link to="/">Search trains</Link></div>}
      {!loading && !error && history?.content.map(booking => <article className="history-card" key={booking.internalBookingReference}>
        <header><span>RailYatra Reference <strong>{booking.internalBookingReference}</strong></span><b className="booking-badge">{booking.currentStatus === 'CONFIRMED' ? 'CNF' : booking.currentStatus === 'WAITLISTED' ? 'WL' : booking.currentStatus}{booking.currentStatusPosition ? ` ${booking.currentStatusPosition}` : ''}</b></header>
        <h2>{booking.trainNumber} · {booking.trainName}</h2><p className="history-route">{booking.origin} → {booking.destination}</p>
        <dl><div><dt>Journey date</dt><dd>{booking.journeyDate}</dd></div><div><dt>Class</dt><dd>{booking.classCode}</dd></div><div><dt>Passengers</dt><dd>{booking.passengerCount}</dd></div><div><dt>Total fare</dt><dd>{new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(booking.totalFare))}</dd></div></dl>
        {booking.providerType === 'LOCAL' && <p className="history-disclaimer">Internal development reservation — not an official Indian Railways ticket.</p>}
        <div className="history-actions"><Link to={`/bookings/${encodeURIComponent(booking.internalBookingReference)}/confirmation`}>VIEW</Link><button disabled={!!busy} onClick={() => download(booking)}>{busy === booking.internalBookingReference ? 'PLEASE WAIT…' : 'DOWNLOAD TICKET'}</button>{booking.canCancel && <Link className="cancel-booking" to={`/bookings/${encodeURIComponent(booking.internalBookingReference)}/cancel`}>CANCEL</Link>}</div>
      </article>)}
    </div>
    {!loading && !error && history && history.totalPages > 0 && <nav className="history-pagination" aria-label="Bookings pagination"><button disabled={page === 0 || !!busy} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1} of {history.totalPages} · {history.totalElements} bookings</span><button disabled={page + 1 >= history.totalPages || !!busy} onClick={() => setPage(value => value + 1)}>Next</button></nav>}
  </section>;
}

import axios from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import './my-bookings.css';
import { activePassengerIds, togglePassenger, quoteExpired } from './cancellationRules.js';
import { useBookingRealtime } from '../realtime/useBookingRealtime';

type Passenger = { id: number; name: string; cancelled: boolean; currentStatus: string };
type Booking = { trainName: string; journeyDate: string; passengers: Passenger[] };
type Quote = { quoteId: string; selectedPassengers: { id: number; name: string }[]; cancellationCharge: number; estimatedRefund: number; expiresAt: string; confirmed: boolean; refundStatus: string; policyNotice: string };
type Refund = { refundReference: string; status: string };
const money = (value: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(value));

export function CancellationPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const { reference = '' } = useParams(); const { accessToken } = useAuth();
  const { revision } = useBookingRealtime(apiBaseUrl, reference);
  const [booking, setBooking] = useState<Booking | null>(null); const [selection, setSelection] = useState<number[]>([]);
  const [quote, setQuote] = useState<Quote | null>(null); const [busy, setBusy] = useState(false);
  const [past, setPast] = useState<Quote[]>([]);
  const [error, setError] = useState(''); const [reload, setReload] = useState(0); const [now, setNow] = useState(Date.now());
  const headers = { Authorization: `Bearer ${accessToken}` };
  const endpoint = `${apiBaseUrl}/api/bookings/${encodeURIComponent(reference)}`;
  useEffect(() => {
    const abort = new AbortController(); setError(''); setBooking(null); setQuote(null); setPast([]);
    axios.get<Booking>(`${apiBaseUrl}/api/bookings/${encodeURIComponent(reference)}`, { headers: { Authorization: `Bearer ${accessToken}` }, signal: abort.signal })
      .then(response => { setBooking(response.data); setSelection(activePassengerIds(response.data.passengers)); })
      .catch(() => { if (!abort.signal.aborted) setError('Could not load this reservation. Check your session and retry.'); });
    axios.get<Quote[]>(`${apiBaseUrl}/api/bookings/${encodeURIComponent(reference)}/cancellations`, { headers: { Authorization: `Bearer ${accessToken}` }, signal: abort.signal })
      .then(response => setPast(response.data)).catch(() => { if (!abort.signal.aborted) setError('Could not load saved cancellation requests. Refresh to verify any previous confirmation.'); });
    return () => abort.abort();
  }, [apiBaseUrl, reference, accessToken, reload]);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (!booking) return;
    const abort = new AbortController();
    const poll = async () => {
      try {
        const response = await axios.get<Refund[]>(`${apiBaseUrl}/api/bookings/${encodeURIComponent(reference)}/refunds`, { headers: { Authorization: `Bearer ${accessToken}` }, signal: abort.signal });
        const states = new Map(response.data.map(item => [item.refundReference, item.status]));
        if (!abort.signal.aborted) {
          setQuote(current => current && states.has(current.quoteId) ? { ...current, refundStatus: states.get(current.quoteId)! } : current);
          setPast(current => current.map(item => states.has(item.quoteId) ? { ...item, refundStatus: states.get(item.quoteId)! } : item));
        }
      } catch { if (!abort.signal.aborted) setError('Could not refresh refund status. Refresh your reservation to check settlement.'); }
    };
    void poll();
    const timer = setInterval(poll, 5000);
    return () => { abort.abort(); clearInterval(timer); };
  }, [booking, apiBaseUrl, accessToken, reference, revision]);
  async function review() {
    setBusy(true); setError(''); setQuote(null);
    try { const response = await axios.post<Quote>(`${endpoint}/cancellation-quote`, { passengerIds: selection }, { headers }); setQuote(response.data); }
    catch (reason) { setError(axios.isAxiosError(reason) ? reason.response?.data?.message || 'Could not quote cancellation. Refresh and try again.' : 'Could not quote cancellation.'); }
    finally { setBusy(false); }
  }
  async function confirm() {
    if (!quote) return;
    setBusy(true); setError('');
    try { const response = await axios.post<Quote>(`${endpoint}/cancel`, { quoteId: quote.quoteId }, { headers }); setQuote(response.data); }
    catch (reason) { setError(axios.isAxiosError(reason) ? reason.response?.data?.message || 'Cancellation was not confirmed. Retry the same confirmation or request a fresh quote.' : 'Cancellation was not confirmed.'); }
    finally { setBusy(false); }
  }
  const expired = quoteExpired(quote, now);
  return <section className="my-bookings"><p className="eyebrow">MANAGE YOUR RESERVATION</p><h1>{quote?.confirmed ? 'Cancellation confirmed' : 'Cancel passengers'}</h1><p>RailYatra Reference: {reference}</p>
    {error && <p role="alert" className="form-error">{error}</p>}
    {!booking && !error && <p role="status">Loading reservation…</p>}
    {booking && !quote?.confirmed && <article className="history-card"><h2>{booking.trainName} · {booking.journeyDate}</h2><p>Select everyone to cancel the full booking, or select individual passengers.</p><button disabled={busy} onClick={() => { setSelection(activePassengerIds(booking.passengers)); setQuote(null); }}>Select all active passengers</button>
      <div className="cancellation-passengers">{booking.passengers.map(passenger => <label key={passenger.id}><input type="checkbox" disabled={busy || passenger.cancelled} checked={selection.includes(passenger.id)} onChange={e => { setSelection(togglePassenger(selection, passenger, e.target.checked)); setQuote(null); }}/><span>{passenger.name} · {passenger.cancelled ? 'CANCELLED' : passenger.currentStatus}</span></label>)}</div>
      <button className="primary-search" disabled={busy || !selection.length} onClick={review}>{busy ? 'Please wait…' : 'Review cancellation'}</button></article>}
    {quote && <article className="history-card"><h2>{quote.confirmed ? 'Cancelled passengers' : 'Review before confirming'}</h2><ul>{quote.selectedPassengers.map(p => <li key={p.id}>{p.name}</li>)}</ul><dl><div><dt>Cancellation charge</dt><dd>{money(quote.cancellationCharge)}</dd></div><div><dt>{quote.confirmed ? 'Refund requested' : 'Estimated refund'}</dt><dd>{money(quote.estimatedRefund)}</dd></div></dl><p>{quote.policyNotice}</p>
      {quote.confirmed ? <><p role="status">Refund status: {quote.refundStatus}. Unselected passengers retain their reservation.</p><Link to={`/bookings/${encodeURIComponent(reference)}/confirmation`}>View updated booking</Link></> : <><p>Review expires: {new Date(quote.expiresAt).toLocaleTimeString()}. Confirmation cannot be undone.</p>{expired && <p role="alert">Quote expired. Review cancellation again.</p>}<button className="primary-search" disabled={busy || expired} onClick={confirm}>{busy ? 'Confirming…' : 'Confirm cancellation'}</button></>}</article>}
    {past.length > 0 && <article className="history-card"><h2>Saved cancellation requests</h2>{past.map(item => <p key={item.quoteId}>{item.selectedPassengers.map(p => p.name).join(', ')} · Charge {money(item.cancellationCharge)} · Refund {money(item.estimatedRefund)} · {item.refundStatus}</p>)}</article>}
    <button disabled={busy} onClick={() => setReload(value => value + 1)}>Refresh reservation</button> <Link to="/my-bookings">Back to My Bookings</Link>
  </section>;
}

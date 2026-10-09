import axios from 'axios';
import { FormEvent, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useBookingRealtime } from '../realtime/useBookingRealtime';

type PnrStatus = { internalReference: string; referenceLabel: string; bookingStatus: string; currentStatus: string; journey: { trainNumber: string; trainName: string; origin: string; destination: string; journeyDate: string; classCode: string; quota: string }; passengers: { passengerNumber: number; passengerName: string; bookingStatus: string; currentStatus: string }[] };

export function PnrStatusPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const [query] = useSearchParams(); const initial = query.get('reference') ?? '';
  const { accessToken } = useAuth();
  const [reference, setReference] = useState(initial); const [requested, setRequested] = useState(initial); const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<PnrStatus | null>(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  const [refunds, setRefunds] = useState<{ refundReference: string; refund: number; status: string }[]>([]);
  const savedReference = status?.internalReference ?? null;
  const { revision, connection } = useBookingRealtime(apiBaseUrl, savedReference);
  useEffect(() => { if (!accessToken) setRefunds([]); }, [accessToken]);
  useEffect(() => {
    setReference(initial); setRequested(initial); setStatus(null);
  }, [initial]);
  useEffect(() => {
    if (!requested.trim()) return;
    const abort = new AbortController(); setLoading(true); setError(''); setRefunds([]);
    axios.get<PnrStatus>(`${apiBaseUrl}/api/pnr/${encodeURIComponent(requested.trim())}`, { signal: abort.signal })
      .then(response => {if(!abort.signal.aborted)setStatus(response.data);}).catch(() => { if (!abort.signal.aborted) setError('Could not find this booking reference. Try again shortly.'); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [requested, attempt, apiBaseUrl]);
  useEffect(() => {
    if (!savedReference || !accessToken || connection !== 'live') return;
    const abort = new AbortController(); const headers = { Authorization: `Bearer ${accessToken}` };
    Promise.all([
      axios.get<PnrStatus>(`${apiBaseUrl}/api/bookings/${encodeURIComponent(savedReference)}/pnr`, { headers, signal: abort.signal }),
      axios.get<typeof refunds>(`${apiBaseUrl}/api/bookings/${encodeURIComponent(savedReference)}/refunds`, { headers, signal: abort.signal })
    ]).then(([snapshot, refundSnapshot]) => { if (!abort.signal.aborted) { setStatus(snapshot.data); setRefunds(refundSnapshot.data); setError(''); } })
      .catch(() => { if (!abort.signal.aborted) setError('Could not refresh saved status. Displayed details may be out of date.'); });
    return () => abort.abort();
  }, [revision, connection, savedReference, accessToken, apiBaseUrl]);
  function lookup(event: FormEvent) { event.preventDefault(); if (!reference.trim()) { setError('Enter your RailYatra booking reference.'); return; } setStatus(null); setRequested(reference.trim().toUpperCase()); setAttempt(value => value + 1); }
  const liveMessage = connection === 'live' ? 'Connected · live status updates' : connection === 'unavailable' ? 'Live updates require an active session and ownership of this booking.' : connection === 'offline' ? 'Sign in as the booking owner for live updates.' : 'Reconnecting to live status…';
  return <section className="pnr-page"><p className="eyebrow">BOOKING STATUS</p><h1>Check your journey status</h1><p className="pnr-help">Enter the RailYatra internal booking reference from your confirmation. It is not an official Indian Railways PNR.</p><form onSubmit={lookup} className="pnr-form"><input aria-label="RailYatra reference" value={reference} onChange={event => setReference(event.target.value.toUpperCase())} placeholder="e.g. RY..." maxLength={32} /><button className="primary-search" disabled={loading}>{loading ? 'Checking…' : 'Check status'}</button></form>{error && <p className="form-error" role="alert">{error}</p>}{status && <article className="pnr-card"><p role="status">{liveMessage}</p><p className="eyebrow">{status.referenceLabel}</p><h2>{status.internalReference}</h2><div className="pnr-statuses"><span>Booking: <b>{status.bookingStatus}</b></span><span>Current: <b>{status.currentStatus}</b></span></div><dl><div><dt>Train</dt><dd>{status.journey.trainNumber} · {status.journey.trainName}</dd></div><div><dt>Journey</dt><dd>{status.journey.origin} → {status.journey.destination} · {status.journey.journeyDate}</dd></div><div><dt>Class / quota</dt><dd>{status.journey.classCode} · {status.journey.quota}</dd></div></dl><h3>Passenger status</h3>{status.passengers.map(passenger => <div className="pnr-passenger" key={passenger.passengerNumber}><span>{passenger.passengerNumber}. {passenger.passengerName}</span><strong>{passenger.currentStatus}</strong></div>)}{refunds.length > 0 && <><h3>Refund status · simulated</h3>{refunds.map(refund => <p key={refund.refundReference}>₹{refund.refund} · {refund.status}</p>)}</>}</article>}</section>;
}

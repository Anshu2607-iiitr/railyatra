import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { connectBookingStream, type ConnectionState } from './bookingStream.js';
export function useBookingRealtime(apiBaseUrl: string, reference: string | null) {
  const { accessToken } = useAuth();
  const [revision, setRevision] = useState(0); const [connection, setConnection] = useState<ConnectionState>('offline');
  useEffect(() => {
    if (!reference || !accessToken) { setConnection('offline'); return; }
    let active = true;
    const url = reference === '*' ? `${apiBaseUrl}/api/bookings/events` : `${apiBaseUrl}/api/pnr/${encodeURIComponent(reference)}/events`;
    const close = connectBookingStream(url, accessToken, event => {
      if (active && ['connected', 'booking-status-change', 'refund-status-change'].includes(event)) setRevision(value => value + 1);
    }, state => { if (active) setConnection(state); });
    return () => { active = false; close(); };
  }, [apiBaseUrl, reference, accessToken]);
  return { revision, connection };
}

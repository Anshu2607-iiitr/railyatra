import { connectBookingStream } from './bookingStream.js';

// Events are invalidations, never authoritative statistics. Connected also forces a fresh GET.
export function connectAdminDashboard(url, token, refresh, onState) {
  return connectBookingStream(url, token, event => {
    if (event === 'connected' || event === 'dashboard-change') refresh();
  }, onState);
}

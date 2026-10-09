import type { ConnectionState } from './bookingStream';
export function connectAdminDashboard(url: string, token: string, refresh: () => void, onState: (state: ConnectionState) => void): () => void;

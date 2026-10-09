export type ConnectionState = 'offline' | 'connecting' | 'live' | 'reconnecting' | 'unavailable';
export function createSseParser(onEvent: (event: string, data: string) => void): (chunk: string) => void;
export function connectBookingStream(url: string, token: string, onEvent: (event: string, data: string) => void, onState: (state: ConnectionState) => void): () => void;

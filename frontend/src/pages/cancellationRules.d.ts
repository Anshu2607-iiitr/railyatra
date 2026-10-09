export function activePassengerIds(passengers: { id: number; cancelled: boolean }[]): number[];
export function togglePassenger(selection: number[], passenger: { id: number; cancelled: boolean }, checked: boolean): number[];
export function quoteExpired(quote: { expiresAt: string } | null, now: number): boolean;

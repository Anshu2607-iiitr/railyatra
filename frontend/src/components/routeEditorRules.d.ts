import type { Station } from './StationAutocomplete';
export type EditableStop = { key: string; station: Station | null; arrival: string; departure: string; dayOffset: string; distanceKm: string };
export type RouteIssue = { index: number; message: string };
export function timeMinutes(value: string): number | null;
export function haltMinutes(stop: Pick<EditableStop, 'arrival' | 'departure'>): number | null;
export function validateRoute(stops: EditableStop[]): RouteIssue[];
export function moveStop(stops: EditableStop[], from: number, to: number): EditableStop[];
export function routePayload(stops: EditableStop[]): { stationCode: string; stopSequence: number; arrival: string | null; departure: string | null; dayOffset: number; distanceKm: number }[];

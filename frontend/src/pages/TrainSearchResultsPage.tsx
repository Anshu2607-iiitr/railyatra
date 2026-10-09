import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { Filter, Map, SlidersHorizontal } from 'lucide-react';
import { useAuth } from '../auth/AuthProvider';
import '../route.css';

type Result = { trainNumber: string; trainName: string; from: string; to: string; departure: string | null; arrival: string | null };
type PageResult = { content: Result[]; totalElements: number };
type Stop = { stationCode: string; stationName: string; arrival: string | null; departure: string | null; haltMinutes: number; distanceKm: number; dayNumber: number; stopSequence: number };
type Availability = { status: 'AVAILABLE' | 'RAC' | 'WL'; count: number; display: string };

const FilterPanel = ({ open }: { open: boolean }) => <aside className={`filter-panel ${open ? 'open' : ''}`}><div className="filter-title"><Filter size={17}/> Filters</div><label>Departure time<select><option>Any time</option><option>Morning</option><option>Afternoon</option><option>Evening</option></select></label><label>Arrival time<select><option>Any time</option><option>Morning</option><option>Afternoon</option><option>Evening</option></select></label><label>Train type<select><option>All types</option><option>Express</option></select></label><label>Class<select><option>All classes</option><option>3A</option><option>SL</option></select></label></aside>;

function Timeline({ apiBaseUrl, trainNumber }: { apiBaseUrl: string; trainNumber: string }) {
  const [stops, setStops] = useState<Stop[] | null>(null);
  useEffect(() => { axios.get<Stop[]>(`${apiBaseUrl}/api/trains/${trainNumber}/route`).then(r => setStops(r.data)); }, [apiBaseUrl, trainNumber]);
  return <div className="route-timeline">{!stops ? 'Loading route…' : stops.map(s => <div className="timeline-stop" key={s.stopSequence}><i/><div><b>{s.stationName} <small>({s.stationCode})</small></b><span>{s.arrival ?? 'Origin'}{s.departure ? ` / ${s.departure}` : ''} · Day {s.dayNumber}</span>{s.haltMinutes > 0 && <em>Halt {s.haltMinutes} min · {s.distanceKm} km</em>}</div></div>)}</div>;
}

function AvailabilityBadge({ apiBaseUrl, trainNumber, date, classCode, quota }: { apiBaseUrl: string; trainNumber: string; date: string; classCode: string; quota: string }) {
  const [availability, setAvailability] = useState<Availability | null>(null); const [error, setError] = useState(false);
  useEffect(() => { if (!classCode) return; let active = true; const refresh = () => axios.get<Availability>(`${apiBaseUrl}/api/availability`, { params: { trainNumber, date, class: classCode, quota } }).then(r => active && setAvailability(r.data)).catch(() => active && setError(true)); refresh(); const stream = new EventSource(`${apiBaseUrl}/api/availability/stream?${new URLSearchParams({ trainNumber, date, class: classCode, quota })}`); stream.addEventListener('availability-change', refresh); stream.addEventListener('connected', refresh); stream.onopen = refresh; return () => { active = false; stream.close(); }; }, [apiBaseUrl, trainNumber, date, classCode, quota]);
  if (!classCode) return <div className="availability unavailable">Select a class to check availability</div>; if (error) return <div className="availability unavailable">Availability unavailable</div>;
  return <div className={`availability ${availability?.status?.toLowerCase() ?? ''}`}>{availability?.display ?? 'Checking availability…'}</div>;
}

export function TrainSearchResultsPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const [params] = useSearchParams(); const [results, setResults] = useState<PageResult | null>(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [drawer, setDrawer] = useState(false); const [expanded, setExpanded] = useState<string | null>(null);
  const { user } = useAuth(); const navigate = useNavigate();
  const from = params.get('from') ?? ''; const to = params.get('to') ?? ''; const date = params.get('date') ?? ''; const classCode = params.get('class') ?? ''; const quota = params.get('quota') ?? 'GENERAL';
  useEffect(() => { const controller = new AbortController(); axios.get<PageResult>(`${apiBaseUrl}/api/trains/search`, { params: { from, to, date, class: classCode || undefined, quota, page: 0, size: 10, sort: 'DEPARTURE' }, signal: controller.signal }).then(x => setResults(x.data)).catch(() => setError('Could not load trains.')).finally(() => setLoading(false)); return () => controller.abort(); }, [apiBaseUrl, from, to, date, classCode, quota]);
  function startBooking(train: Result) { const bookingUrl = `/book?${new URLSearchParams({ trainNumber: train.trainNumber, trainName: train.trainName, from, to, date, class: classCode, quota }).toString()}`; if (!user) navigate('/login', { state: { from: bookingUrl } }); else navigate(bookingUrl); }
  return <section className="results-page"><Link to="/" className="back-link">← Modify journey</Link><h1>{from} to {to}</h1><div className="results-toolbar"><button className="filter-trigger" onClick={() => setDrawer(!drawer)}><SlidersHorizontal size={17}/> Filters</button></div><div className="results-layout"><FilterPanel open={drawer}/><div className="result-list">{loading && <div className="train-skeleton"><i/><i/><i/></div>}{error && <div className="search-error">{error}</div>}{results && results.content.map(train => <article className="train-card" key={train.trainNumber}><div className="train-heading"><div><strong>{train.trainNumber}</strong><span>{train.trainName}</span></div></div><div className="journey-line"><div><b>{train.from}</b><span>{train.departure ?? '—'}</span></div><div className="duration"><i/><span>Scheduled route</span></div><div><b>{train.to}</b><span>{train.arrival ?? '—'}</span></div></div><div className="class-row"><div><b>{classCode || 'Select class'}</b><span>Fare requires fare provider</span></div><AvailabilityBadge apiBaseUrl={apiBaseUrl} trainNumber={train.trainNumber} date={date} classCode={classCode} quota={quota}/><button onClick={() => startBooking(train)} disabled={!classCode}>BOOK NOW</button><button className="route-button" onClick={() => setExpanded(expanded === train.trainNumber ? null : train.trainNumber)}><Map size={16}/> VIEW ROUTE</button></div>{expanded === train.trainNumber && <Timeline apiBaseUrl={apiBaseUrl} trainNumber={train.trainNumber}/>}</article>)}</div></div></section>;
}

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { Plus, Search, ArrowUp, ArrowDown, X } from 'lucide-react';
import { useAuth } from '../auth/AuthProvider';
import { StationAutocomplete, type Station } from '../components/StationAutocomplete';
import './admin-trains.css';

type Stop = { station: Station; stopSequence: number; arrival: string | null; departure: string | null; dayOffset: number; distanceKm: number };
type Train = { id: number; trainNumber: string; name: string; type: string; active: boolean; version: number; dataSource: string; source: string; destination: string; runningDays: string[]; classes: string[]; stops: Stop[]; configurationLocked: boolean };
type Page = { content: Train[]; page: number; totalElements: number; totalPages: number };
type Options = { types: string[]; runningDays: string[]; classes: { code: string; name: string }[] };
type EditableStop = { key: string; station: Station | null; arrival: string; departure: string; dayOffset: string; distanceKm: string };
const label = (text: string) => text.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
function message(error: unknown) {
  if (axios.isAxiosError(error)) {
    if ([401, 403].includes(error.response?.status ?? 0)) return 'Administrator access is required. Sign in again.';
    const body = error.response?.data;
    if (body?.fields) return Object.entries(body.fields).map(([key, value]) => `${key}: ${value}`).join('; ');
    return typeof body?.message === 'string' ? body.message : 'The server could not complete this request. Please retry.';
  }
  return 'The request could not be completed.';
}
function emptyStop(first: boolean): EditableStop {
  return { key: crypto.randomUUID(), station: null, arrival: first ? '' : '14:00', departure: first ? '08:00' : '', dayOffset: '0', distanceKm: first ? '0' : '' };
}

function DeactivateDialog({ train, busy, error, onCancel, onConfirm }: { train: Train; busy: boolean; error: string; onCancel: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close(); }, []);
  return <dialog ref={dialog} aria-labelledby="deactivate-title" className="train-confirm" onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}><h2 id="deactivate-title">Deactivate {train.trainNumber}?</h2><p>{train.name} will leave search results and stop accepting new holds. Existing tickets and valid checkout holds are retained.</p><div className="train-form-actions"><button autoFocus className="train-secondary" disabled={busy} onClick={onCancel}>Keep active</button><button className="train-danger" disabled={busy} onClick={onConfirm}>{busy ? 'Deactivating…' : 'Confirm deactivation'}</button></div>{error && <p role="alert" className="admin-error">{error}</p>}</dialog>;
}

function TrainEditor({ train, options, apiBaseUrl, onSaved, onClose }: { train: Train | null; options: Options; apiBaseUrl: string; onSaved: (train: Train) => void; onClose: () => void }) {
  const { accessToken } = useAuth();
  const [number, setNumber] = useState(train?.trainNumber ?? '');
  const [name, setName] = useState(train?.name ?? '');
  const [type, setType] = useState(train?.type ?? options.types[0]);
  const [days, setDays] = useState<string[]>(train?.runningDays ?? []);
  const [classes, setClasses] = useState<string[]>(train?.classes ?? []);
  const [stops, setStops] = useState<EditableStop[]>(train?.stops.length ? train.stops.map(stop => ({ key: crypto.randomUUID(), station: stop.station, arrival: stop.arrival?.slice(0, 5) ?? '', departure: stop.departure?.slice(0, 5) ?? '', dayOffset: String(stop.dayOffset), distanceKm: String(stop.distanceKm) })) : [emptyStop(true), emptyStop(false)]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const locked = train?.configurationLocked ?? false;
  const toggle = (items: string[], item: string) => items.includes(item) ? items.filter(value => value !== item) : [...items, item];
  function changeStop(index: number, values: Partial<EditableStop>) { setStops(rows => rows.map((row, i) => i === index ? { ...row, ...values } : row)); }
  function move(index: number, direction: number) { setStops(rows => { const copy = [...rows]; [copy[index], copy[index + direction]] = [copy[index + direction], copy[index]]; return copy; }); }
  async function save(event: FormEvent) {
    event.preventDefault(); setError('');
    if (!days.length || !classes.length) { setError('Select at least one running day and one class.'); return; }
    if (stops.some(stop => !stop.station)) { setError('Select a valid station for every route stop.'); return; }
    const codes = stops.map(stop => stop.station!.code);
    if (new Set(codes).size !== codes.length) { setError('Source, destination and intermediate stations must be different.'); return; }
    const payload = { trainNumber: number.trim().toUpperCase(), name: name.trim(), type, source: codes[0], destination: codes[codes.length - 1], runningDays: days, classes, version: train?.version,
      stops: stops.map((stop, index) => ({ stationCode: stop.station!.code, stopSequence: index + 1, arrival: index === 0 ? null : stop.arrival || null, departure: index === stops.length - 1 ? null : stop.departure || null, dayOffset: Number(stop.dayOffset), distanceKm: Number(stop.distanceKm) })) };
    setSaving(true);
    try {
      const config = { headers: { Authorization: `Bearer ${accessToken}` } };
      const response = train ? await axios.put<Train>(`${apiBaseUrl}/api/admin/trains/${train.id}`, payload, config) : await axios.post<Train>(`${apiBaseUrl}/api/admin/trains`, payload, config);
      onSaved(response.data);
    } catch (reason) { setError(message(reason)); } finally { setSaving(false); }
  }
  return <section className="train-editor" aria-labelledby="train-editor-title">
    <div className="train-editor-heading"><h2 id="train-editor-title">{train ? `Edit ${train.trainNumber}` : 'Create train'}</h2><button type="button" className="train-secondary" onClick={onClose} disabled={saving}><X size={16}/> Close</button></div>
    <p className="train-note">Internal railway configuration. New classes need coach, seat and fare configuration before bookings can open.</p>
    {locked && <p className="train-warning">Bookings or active holds protect this train’s operational configuration. You can update its name and type.</p>}
    {!locked && train && <p className="train-note">Changing route stops or timings disables existing fare rules for review.</p>}
    <form onSubmit={save}>
      <fieldset disabled={saving} className="train-main-fields"><legend className="sr-only">Train details</legend>
        <label>Train number<input required maxLength={10} pattern="[A-Za-z0-9]{1,10}" value={number} onChange={e => setNumber(e.target.value.toUpperCase())} disabled={locked} placeholder="e.g. 18625"/></label>
        <label>Train name<input required maxLength={160} value={name} onChange={e => setName(e.target.value)}/></label>
        <label>Train type<select value={type} onChange={e => setType(e.target.value)}>{options.types.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label>
      </fieldset>
      <fieldset disabled={saving || locked} className="train-options"><legend>Running days</legend><div className="train-checkboxes">{options.runningDays.map(day => <label key={day}><input type="checkbox" checked={days.includes(day)} onChange={() => setDays(toggle(days, day))}/>{label(day).slice(0, 3)}</label>)}</div><button type="button" className="train-text-button" onClick={() => setDays(days.length === 7 ? [] : options.runningDays)}>{days.length === 7 ? 'Clear days' : 'Every day'}</button></fieldset>
      <fieldset disabled={saving || locked} className="train-options"><legend>Classes</legend><div className="train-checkboxes">{options.classes.map(cls => <label key={cls.code} title={cls.name}><input type="checkbox" checked={classes.includes(cls.code)} onChange={() => setClasses(toggle(classes, cls.code))}/>{cls.code}</label>)}</div></fieldset>
      <fieldset disabled={saving || locked} className="train-route-editor"><legend>Ordered route</legend><p className="train-note">First stop is the source; last is the destination. Day offset starts at 0. Times and cumulative distance must increase along the route.</p>
        {stops.map((stop, index) => <div className="train-stop-editor" key={stop.key}>
          <div className="train-stop-station"><span className="train-stop-number">{index + 1}</span><StationAutocomplete apiBaseUrl={apiBaseUrl} label={index === 0 ? 'Source station' : index === stops.length - 1 ? 'Destination station' : 'Intermediate station'} selectedStation={stop.station} onSelect={station => changeStop(index, { station })}/></div>
          <div className="train-stop-times">
            <label>Arrival<input aria-label={`Stop ${index + 1} arrival`} type="time" required={index > 0} disabled={index === 0} value={stop.arrival} onChange={e => changeStop(index, { arrival: e.target.value })}/></label>
            <label>Departure<input aria-label={`Stop ${index + 1} departure`} type="time" required={index < stops.length - 1} disabled={index === stops.length - 1} value={stop.departure} onChange={e => changeStop(index, { departure: e.target.value })}/></label>
            <label>Day offset<input type="number" min="0" max="14" step="1" required readOnly={index === 0} value={stop.dayOffset} onChange={e => changeStop(index, { dayOffset: e.target.value })}/></label>
            <label>Distance (km)<input type="number" min="0" max="30000" step="1" required readOnly={index === 0} value={stop.distanceKm} onChange={e => changeStop(index, { distanceKm: e.target.value })}/></label>
          </div>
          {index > 0 && index < stops.length - 1 && <div className="train-stop-actions"><button type="button" aria-label={`Move stop ${index + 1} up`} disabled={index === 1} onClick={() => move(index, -1)}><ArrowUp size={15}/></button><button type="button" aria-label={`Move stop ${index + 1} down`} disabled={index === stops.length - 2} onClick={() => move(index, 1)}><ArrowDown size={15}/></button><button type="button" onClick={() => setStops(rows => rows.filter((_, i) => i !== index))}>Remove</button></div>}
        </div>)}
        <button type="button" className="train-secondary" disabled={stops.length >= 100} onClick={() => setStops(rows => [...rows.slice(0, -1), { ...emptyStop(false), arrival: '', departure: '' }, rows[rows.length - 1]])}><Plus size={16}/> Add intermediate stop</button>
      </fieldset>
      {error && <p className="admin-error" role="alert">{error}</p>}
      <div className="train-form-actions"><button type="submit" className="train-primary" disabled={saving}>{saving ? 'Saving…' : train ? 'Save changes' : 'Create train'}</button><button type="button" className="train-secondary" onClick={onClose} disabled={saving}>Cancel</button></div>
    </form>
  </section>;
}

export function AdminTrainsPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const { accessToken } = useAuth();
  const [data, setData] = useState<Page | null>(null);
  const [options, setOptions] = useState<Options | null>(null);
  const [query, setQuery] = useState(''); const [filter, setFilter] = useState(''); const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0); const [editing, setEditing] = useState<Train | 'new' | null>(null);
  const [deactivating, setDeactivating] = useState<Train | null>(null); const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!accessToken) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true); setError('');
      const config = { headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal };
      Promise.all([axios.get<Page>(`${apiBaseUrl}/api/admin/trains`, { ...config, params: { q: query, active: filter || undefined, page, size: 20 } }), axios.get<Options>(`${apiBaseUrl}/api/admin/trains/options`, config)])
        .then(([rows, supported]) => { setData(rows.data); setOptions(supported.data); })
        .catch(reason => { if (!controller.signal.aborted) { setData(null); setError(message(reason)); } })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 250);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [apiBaseUrl, accessToken, query, filter, page, revision]);
  async function edit(train: Train) {
    setBusy(true); setError(''); setNotice('');
    try { const response = await axios.get<Train>(`${apiBaseUrl}/api/admin/trains/${train.id}`, { headers: { Authorization: `Bearer ${accessToken}` } }); setEditing(response.data); }
    catch (reason) { setError(message(reason)); } finally { setBusy(false); }
  }
  async function deactivate() {
    if (!deactivating) return;
    setBusy(true); setError('');
    try {
      await axios.patch(`${apiBaseUrl}/api/admin/trains/${deactivating.id}/deactivate`, { version: deactivating.version }, { headers: { Authorization: `Bearer ${accessToken}` } });
      setNotice(`${deactivating.trainNumber} deactivated. Existing bookings are retained.`); setDeactivating(null); setRevision(value => value + 1);
    } catch (reason) { setError(message(reason)); } finally { setBusy(false); }
  }
  return <section className="admin-dashboard admin-trains">
    <Link to="/admin" className="train-back">← Operations dashboard</Link>
    <div className="admin-heading"><div><p className="admin-eyebrow">Railway configuration</p><h1>Manage trains</h1><p>Create services, maintain routes and running days, or close new reservations.</p></div><button className="train-primary" disabled={!options || busy || editing !== null} onClick={() => { setEditing('new'); setNotice(''); setError(''); }}><Plus size={17}/> Create train</button></div>
    {notice && <p className="train-success" role="status">{notice}</p>}
    {error && <p className="admin-error" role="alert">{error} <button className="train-text-button" onClick={() => setRevision(value => value + 1)}>Reload</button></p>}
    {editing && options ? <TrainEditor key={editing === 'new' ? 'new' : `${editing.id}-${editing.version}`} train={editing === 'new' ? null : editing} options={options} apiBaseUrl={apiBaseUrl} onClose={() => setEditing(null)} onSaved={train => { setEditing(null); setNotice(`${train.trainNumber} saved successfully.`); setRevision(value => value + 1); }}/>
      : <><div className="train-list-toolbar"><label><Search size={17}/><input aria-label="Search trains by number or name" maxLength={80} value={query} placeholder="Search number or name" onChange={e => { setQuery(e.target.value); setPage(0); }}/></label><select aria-label="Filter active trains" value={filter} onChange={e => { setFilter(e.target.value); setPage(0); }}><option value="">All trains</option><option value="true">Active</option><option value="false">Inactive</option></select></div>
        {loading ? <p className="train-empty" role="status">Loading trains…</p> : data && <><p className="train-note">{data.totalElements.toLocaleString('en-IN')} trains · Page {data.totalPages ? page + 1 : 0} of {data.totalPages}</p>
          {!data.content.length ? <p className="train-empty">No trains match this search.</p> : <div className="train-table-scroll"><table className="admin-train-table"><thead><tr><th>Train</th><th>Route / classes</th><th>Running days</th><th>Status</th><th>Actions</th></tr></thead><tbody>{data.content.map(train => <tr key={train.id}><td><strong>{train.trainNumber}</strong><span>{train.name}</span><small>{label(train.type)}</small></td><td><strong>{train.source ?? 'Unconfigured'} → {train.destination ?? 'Unconfigured'}</strong><span>{train.classes.join(' · ') || 'No active classes'}</span></td><td>{train.runningDays.map(day => label(day).slice(0, 3)).join(' · ') || 'Not scheduled'}</td><td><span className={train.active ? 'train-active' : 'train-inactive'}>{train.active ? 'Active' : 'Inactive'}</span><small>{train.dataSource}</small></td><td><div className="train-row-actions"><button className="train-secondary" disabled={busy} onClick={() => edit(train)}>Edit</button>{train.active && <button className="train-danger" disabled={busy} onClick={() => { setDeactivating(train); setError(''); }}>Deactivate</button>}</div></td></tr>)}</tbody></table></div>}
          <div className="train-pagination"><button className="train-secondary" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><button className="train-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}>Next</button></div>
        </>}
      </>}
    {deactivating && <DeactivateDialog train={deactivating} busy={busy} error={error} onCancel={() => setDeactivating(null)} onConfirm={deactivate}/>}
  </section>;
}

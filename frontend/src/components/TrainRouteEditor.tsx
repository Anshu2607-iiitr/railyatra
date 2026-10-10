import { useState } from 'react';
import { ArrowUp, ArrowDown, Plus, Trash2, MapPin } from 'lucide-react';
import { StationAutocomplete } from './StationAutocomplete';
import { haltMinutes, moveStop, validateRoute, type EditableStop } from './routeEditorRules';
import './train-route-editor.css';

type Props = { stops: EditableStop[]; onChange: (stops: EditableStop[]) => void; apiBaseUrl: string; disabled: boolean; showValidation: boolean };
export function TrainRouteEditor({ stops, onChange, apiBaseUrl, disabled, showValidation }: Props) {
  const [announcement, setAnnouncement] = useState('');
  const issues = validateRoute(stops);
  function change(index: number, values: Partial<EditableStop>) {
    onChange(stops.map((stop, i) => i === index ? { ...stop, ...values } : stop));
  }
  function move(index: number, target: number) {
    const updated = moveStop(stops, index, target);
    if (updated === stops) return;
    onChange(updated);
    setAnnouncement(`${stops[index].station?.name ?? 'Stop'} moved to stop ${target + 1}. Check times and distance before saving.`);
  }
  function insert(after: number) {
    const stop: EditableStop = { key: crypto.randomUUID(), station: null, arrival: '', departure: '', dayOffset: stops[after].dayOffset, distanceKm: '' };
    onChange([...stops.slice(0, after + 1), stop, ...stops.slice(after + 1)]);
    setAnnouncement(`Stop ${after + 2} added. Choose a station and enter its schedule.`);
  }
  const last = stops[stops.length - 1];
  return <fieldset disabled={disabled} className="visual-route">
    <legend><MapPin size={17}/> Visual route editor</legend>
    <div className="route-editor-summary">
      <strong>{stops[0]?.station?.code ?? 'Source'} <span aria-hidden="true">→</span> {last?.station?.code ?? 'Destination'}</strong>
      <span>{stops.length} stops</span>
      <span>{last?.distanceKm || '—'} km</span>
      <span className={issues.length ? 'route-review' : 'route-valid'}>{issues.length ? 'Needs review' : 'Route ready'}</span>
    </div>
    <p className="train-note">Source stays first and destination stays last. Move intermediate stops with the arrows or position selector. Times use the 24-hour clock; Day 1 is the departure day. Halt is calculated from arrival and departure on the same day.</p>
    <p className="sr-only" role="status" aria-live="polite">{announcement}</p>
    <ol className="route-editor-timeline">
      {stops.map((stop, index) => {
        const first = index === 0, final = index === stops.length - 1;
        const halt = haltMinutes(stop);
        const rowIssues = issues.filter(issue => issue.index === index);
        const show = showValidation || !!stop.station;
        return <li key={stop.key} className="route-editor-stop">
          <span className="route-editor-marker" aria-label={`Stop number ${index + 1}`}>{index + 1}</span>
          <div className="route-editor-card">
            <div className="route-editor-stop-heading"><strong>{first ? 'Source' : final ? 'Destination' : 'Intermediate stop'}</strong><span>Stop {index + 1}{first || final ? ' · Fixed position' : ''}</span></div>
            <StationAutocomplete apiBaseUrl={apiBaseUrl} label={`Stop ${index + 1} station`} selectedStation={stop.station} onSelect={station => change(index, { station })}/>
            <div className="route-editor-fields">
              <label>Arrival<input aria-label={`Stop ${index + 1} arrival`} type="time" step="1" required={!first} disabled={first} value={stop.arrival} onChange={e => change(index, { arrival: e.target.value })}/></label>
              <label>Departure<input aria-label={`Stop ${index + 1} departure`} type="time" step="1" required={!final} disabled={final} value={stop.departure} onChange={e => change(index, { departure: e.target.value })}/></label>
              <label>Halt (minutes)<output aria-label={`Stop ${index + 1} calculated halt`}>{first || final ? '—' : halt === null ? 'Check times' : Number(halt.toFixed(2))}</output></label>
              <label>Journey day<input aria-label={`Stop ${index + 1} journey day`} type="number" min="1" max="15" step="1" required readOnly={first} value={stop.dayOffset === '' ? '' : Number(stop.dayOffset) + 1} onChange={e => change(index, { dayOffset: e.target.value === '' ? '' : String(Number(e.target.value) - 1) })}/></label>
              <label>Distance (km)<input aria-label={`Stop ${index + 1} distance`} type="number" min="0" max="30000" step="1" required readOnly={first} value={stop.distanceKm} onChange={e => change(index, { distanceKm: e.target.value })}/></label>
            </div>
            {!first && !final && <div className="route-editor-controls">
              <button type="button" className="train-secondary" aria-label={`Move stop ${index + 1} up`} disabled={index === 1} onClick={() => move(index, index - 1)}><ArrowUp size={16}/> Up</button>
              <button type="button" className="train-secondary" aria-label={`Move stop ${index + 1} down`} disabled={index === stops.length - 2} onClick={() => move(index, index + 1)}><ArrowDown size={16}/> Down</button>
              <label>Move to stop<select aria-label={`Position for stop ${index + 1}`} value={index} onChange={e => move(index, Number(e.target.value))}>{stops.slice(1, -1).map((_, i) => <option key={i + 1} value={i + 1}>{i + 2}</option>)}</select></label>
              <button type="button" className="train-danger" aria-label={`Remove stop ${index + 1}`} onClick={() => { onChange(stops.filter(row => row.key !== stop.key)); setAnnouncement(`Stop ${index + 1} removed. Stop numbers updated.`); }}><Trash2 size={15}/> Remove</button>
            </div>}
            {show && rowIssues.length > 0 && <ul className="route-editor-errors" aria-label={`Stop ${index + 1} validation issues`}>{rowIssues.map(issue => <li key={issue.message}>{issue.message}</li>)}</ul>}
          </div>
          {!final && <button type="button" className="route-insert train-text-button" disabled={stops.length >= 100} onClick={() => insert(index)}><Plus size={14}/> Add stop after {index + 1}</button>}
        </li>;
      })}
    </ol>
    {showValidation && issues.some(issue => issue.index === -1) && <p className="admin-error" role="alert">{issues.find(issue => issue.index === -1)?.message}</p>}
  </fieldset>;
}

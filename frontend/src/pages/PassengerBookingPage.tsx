import axios from 'axios';
import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { BookmarkPlus, Trash2, UserRoundPlus } from 'lucide-react';
import { useAuth } from '../auth/AuthProvider';
import './passenger.css';

type Gender = 'MALE' | 'FEMALE' | 'OTHER';
type BerthPreference = 'NO_PREFERENCE' | 'LOWER' | 'MIDDLE' | 'UPPER' | 'SIDE_LOWER' | 'SIDE_UPPER';
type Passenger = { id?: number; name: string; age: string; gender: Gender | ''; berthPreference: BerthPreference };
type SavedPassenger = { id: number; name: string; age: number; gender: Gender; berthPreference: BerthPreference };
const emptyPassenger = (): Passenger => ({ name: '', age: '', gender: '', berthPreference: 'NO_PREFERENCE' });
const labels: Record<BerthPreference, string> = { NO_PREFERENCE: 'No preference', LOWER: 'Lower', MIDDLE: 'Middle', UPPER: 'Upper', SIDE_LOWER: 'Side lower', SIDE_UPPER: 'Side upper' };

export function PassengerBookingPage({ apiBaseUrl }: { apiBaseUrl: string }) {
  const [query] = useSearchParams(); const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [passengers, setPassengers] = useState<Passenger[]>([emptyPassenger()]); const [saved, setSaved] = useState<SavedPassenger[]>([]); const [loadingSaved, setLoadingSaved] = useState(true); const [error, setError] = useState(''); const [message, setMessage] = useState(''); const [submitting, setSubmitting] = useState(false);
  const headers = { Authorization: `Bearer ${accessToken}` };
  useEffect(() => { if (!accessToken) return; axios.get<SavedPassenger[]>(`${apiBaseUrl}/api/saved-passengers`, { headers }).then(r => setSaved(r.data)).catch(() => setError('Could not load saved passengers.')).finally(() => setLoadingSaved(false)); }, [apiBaseUrl, accessToken]);
  const update = (index: number, field: keyof Passenger, value: string) => setPassengers(current => current.map((p, i) => i === index ? { ...p, [field]: value } : p));
  const addPassenger = () => { if (passengers.length < 6) setPassengers(current => [...current, emptyPassenger()]); };
  const removePassenger = (index: number) => { if (passengers.length > 1) setPassengers(current => current.filter((_, i) => i !== index)); };
  function useSaved(passenger: SavedPassenger) { if (passengers.length >= 6) { setError('A booking can have a maximum of 6 passengers.'); return; } setPassengers(current => [...current, { ...passenger, age: String(passenger.age) }]); }
  async function savePassenger(index: number) {
    setError(''); const passenger = passengers[index];
    try { const response = await axios.post<SavedPassenger>(`${apiBaseUrl}/api/saved-passengers`, { ...passenger, age: Number(passenger.age) }, { headers }); setSaved(current => [...current, response.data]); setMessage(`${response.data.name} was saved for future bookings.`); }
    catch (reason) { setError(axios.isAxiosError(reason) ? String(reason.response?.data?.message || 'Could not save passenger.') : 'Could not save passenger.'); }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setMessage(''); setSubmitting(true);
    try {
      const validated = await axios.post<Passenger[]>(`${apiBaseUrl}/api/passengers/validate`, { passengers: passengers.map(p => ({ ...p, age: Number(p.age) })) }, { headers });
      setPassengers(validated.data.map(p => ({ ...p, age: String(p.age) })));
      const hold = await axios.post<{ id: string }>(`${apiBaseUrl}/api/seat-holds`, { trainNumber: query.get('trainNumber'), journeyDate: query.get('date'), classCode: query.get('class'), quota: query.get('quota'), passengers: validated.data }, { headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() } });
      sessionStorage.setItem(`railyatra.checkout.${hold.data.id}`, JSON.stringify({ from: query.get('from'), to: query.get('to'), passengers: validated.data }));
      navigate(`/checkout?${new URLSearchParams({ holdId: hold.data.id }).toString()}`);
    }
    catch (reason) { setError(axios.isAxiosError(reason) ? String(reason.response?.data?.message || 'Please correct the passenger details.') : 'Please correct the passenger details.'); }
    finally { setSubmitting(false); }
  }
  const trainName = query.get('trainName') ?? 'Selected train';
  return <section className="passenger-page"><Link className="back-link" to={`/trains?${query.toString()}`}>← Back to train results</Link><div className="booking-summary"><span>Passenger details</span><strong>{trainName}</strong><small>{query.get('from')} → {query.get('to')} · {query.get('date')} · {query.get('class')} · {query.get('quota')}</small></div><div className="passenger-layout"><form className="passenger-form" onSubmit={submit}><h1>Add travellers</h1><p className="passenger-help">Enter up to 6 passengers. These details are validated by RailYatra before any seat hold or payment.</p>{passengers.map((passenger, index) => <fieldset className="passenger-card" key={index}><legend>Passenger {index + 1}</legend><label>Name<input value={passenger.name} onChange={e => update(index, 'name', e.target.value)} maxLength={140} required /></label><div className="passenger-fields"><label>Age<input type="number" min="1" max="125" value={passenger.age} onChange={e => update(index, 'age', e.target.value)} required /></label><label>Gender<select value={passenger.gender} onChange={e => update(index, 'gender', e.target.value)} required><option value="" disabled>Select</option><option value="MALE">Male</option><option value="FEMALE">Female</option><option value="OTHER">Other</option></select></label></div><label>Berth preference<select value={passenger.berthPreference} onChange={e => update(index, 'berthPreference', e.target.value)}>{Object.entries(labels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><div className="passenger-actions"><button type="button" className="text-action" onClick={() => savePassenger(index)}><BookmarkPlus size={16}/> Save passenger</button>{passengers.length > 1 && <button type="button" className="text-action remove" onClick={() => removePassenger(index)}><Trash2 size={16}/> Remove</button>}</div></fieldset>)}{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success">{message}</p>}<div className="form-footer"><button type="button" className="add-passenger" onClick={addPassenger} disabled={passengers.length >= 6}><UserRoundPlus size={17}/> + ADD PASSENGER</button><button className="primary-search" disabled={submitting}>{submitting ? 'Validating…' : 'Continue'}</button></div></form><aside className="saved-passengers"><h2>Saved passengers</h2>{loadingSaved && <p>Loading saved travellers…</p>}{!loadingSaved && saved.length === 0 && <p>Save a traveller above to reuse their details later.</p>}{saved.map(passenger => <button type="button" className="saved-passenger" key={passenger.id} onClick={() => useSaved(passenger)} disabled={passengers.length >= 6}><b>{passenger.name}</b><span>{passenger.age} years · {passenger.gender.toLowerCase()}</span><small>{labels[passenger.berthPreference]} berth</small></button>)}</aside></div></section>;
}

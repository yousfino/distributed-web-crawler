import { useEffect, useRef, useState } from 'react';
import { ArrowRight, Check, ChevronRight, Download, ExternalLink, Files, FlaskConical, Link2, LoaderCircle, Plus, Search, ShieldCheck, AlertTriangle } from 'lucide-react';
import type { MaterialInventory, MaterialFinding, MaterialSummary, MaterialKind, MaterialReview, MaterialAccess } from '../types/materials';

const base = '/api/materials/inventories';
const kinds: Record<MaterialKind, string> = { DATASET: 'Dataset', CODE: 'Code', SUPPLEMENT: 'Supplement', PROTOCOL: 'Protocol', PROJECT: 'Project page', REPOSITORY: 'Repository record' };
const reviews: Record<MaterialReview, string> = { UNREVIEWED: 'Unreviewed', VERIFIED: 'Verified by researcher', REJECTED: 'Rejected', FOLLOW_UP: 'Follow up' };
const accessLabels: Record<MaterialAccess, string> = { RESPONDED: 'HTTP responded', NOT_FOUND: 'Not found (404/410)', RESTRICTED: 'Access denied', RATE_LIMITED: 'Rate limited', ERROR: 'Server error', UNKNOWN: 'Access unknown', UNCHECKED: 'Not checked' };
const field = 'w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-teal-400 focus:outline-none';
const primary = 'inline-flex items-center justify-center gap-2 rounded-lg bg-teal-400 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-300 disabled:opacity-40';
const secondary = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm hover:bg-slate-800 disabled:opacity-40';
const active = (i: MaterialInventory) => i.phase === 'QUEUED' || i.phase === 'SCANNING';
const date = (value: string | null) => value ? new Date(value).toLocaleString() : 'Not checked';
function Link({ url, children }: { url: string; children: React.ReactNode }) {
  if (!/^https?:\/\//i.test(url)) return null;
  return <a className="inline-flex max-w-full items-center gap-1 break-all text-teal-300 hover:underline" href={url} target="_blank" rel="noopener noreferrer">{children}<ExternalLink className="shrink-0" size={12} /></a>;
}
async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(path, { method, headers: body ? { 'Content-Type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined });
  if (!response.ok) { const error = await response.json().catch(() => ({})) as { message?: string }; throw new Error(error.message || `Request failed (${response.status}). Please try again.`); }
  return response.json() as Promise<T>;
}
function FindingReview({ finding, disabled, save }: { finding: MaterialFinding; disabled: boolean; save: (id: string, decision: MaterialReview, notes: string) => Promise<void> }) {
  const [decision, setDecision] = useState(finding.review);
  const [notes, setNotes] = useState(finding.notes);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  return <div className="grid gap-6 border-t border-slate-800 bg-slate-950/70 p-5 lg:grid-cols-2">
    <div className="min-w-0 space-y-4">
      <div><h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">HTTP check</h4><p className="text-sm text-slate-300">{finding.check.details}</p><p className="mt-2 text-xs text-slate-500">Status: {finding.check.httpStatus ?? 'Unknown'} · Checked: {date(finding.check.checkedAt)}</p><div className="mt-2 text-xs"><Link url={finding.check.finalUrl}>Open checked destination</Link></div></div>
      <div><h4 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">Discovery evidence ({finding.evidence.length})</h4><ul className="space-y-3">{finding.evidence.map((e, index) => <li key={`${e.pageUrl}-${index}`} className="rounded-lg border border-slate-800 p-3 text-xs">
        <Link url={e.pageUrl}>Source page</Link><p className="mt-2 break-all text-slate-500">Paper: {e.paperUrl}</p><p className="mt-2 text-slate-300">Link text: “{e.anchorText || '(empty)'}”</p><blockquote className="mt-2 border-l-2 border-teal-800 pl-3 leading-relaxed text-slate-400">{e.context}</blockquote><p className="mt-2 text-slate-400">Why flagged: {e.reason}</p><p className="mt-2 text-slate-500">Found: {date(e.discoveredAt)}</p>
      </li>)}</ul></div>
    </div>
    <form className="space-y-3" onSubmit={async e => { e.preventDefault(); setSaving(true); setError(''); setSaved(false); try { await save(finding.id, decision, notes); setSaved(true); } catch (err) { setError(err instanceof Error ? err.message : 'Review could not be saved.'); } finally { setSaving(false); } }}>
      <h4 className="flex items-center gap-2 text-sm font-semibold"><ShieldCheck size={16} className="text-teal-300" />Researcher verification</h4><p className="text-xs leading-relaxed text-slate-400">Open the resource and assess its relevance and contents. A successful HTTP check alone does not verify a dataset, code, or permission to reuse it.</p>
      <label className="block text-xs text-slate-400">Review decision<select aria-label="Resource review decision" className={`${field} mt-1`} value={decision} disabled={disabled || saving} onChange={e => { setDecision(e.target.value as MaterialReview); setSaved(false); }}>{Object.entries(reviews).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="block text-xs text-slate-400">Verification notes<textarea aria-label="Verification notes" className={`${field} mt-1 min-h-32`} value={notes} maxLength={10000} disabled={disabled || saving} onChange={e => { setNotes(e.target.value); setSaved(false); }} placeholder="What did you verify? Note relevance, versions, missing files, access requirements, or follow-up actions." /></label>
      <button className={primary} disabled={disabled || saving}>{saving ? <LoaderCircle size={15} className="animate-spin" /> : <Check size={15} />}Save review</button>
      {disabled && <p className="text-xs text-slate-400">Review findings after the scan finishes.</p>}{saved && <p role="status" className="text-xs text-teal-300">Review saved.</p>}{error && <p role="alert" className="text-xs text-rose-300">{error}</p>}
    </form>
  </div>;
}

export function MaterialsInventory({ seed }: { seed?: { url: string; title: string } }) {
  const [list, setList] = useState<MaterialSummary[]>([]);
  const [inventory, setInventory] = useState<MaterialInventory | null>(null);
  const [name, setName] = useState(seed?.title.slice(0, 200) || 'Research materials review');
  const [urls, setUrls] = useState(seed?.url || '');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [pollError, setPollError] = useState('');
  const [showForm, setShowForm] = useState(!!seed);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [kind, setKind] = useState('');
  const [review, setReview] = useState('');
  const [access, setAccess] = useState('');
  const [text, setText] = useState('');
  const [exportScope, setExportScope] = useState('');
  const [exporting, setExporting] = useState(false);
  const selection = useRef(0);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    let alive = true;
    void api<MaterialSummary[]>(base).then(async summaries => {
      if (!alive) return; setList(summaries);
      if (summaries[0] && !seed) { const item = await api<MaterialInventory>(`${base}/${summaries[0].id}`); if (alive) setInventory(item); }
    }).catch(e => { if (alive) setError(e instanceof Error ? e.message : 'Could not load inventories.'); }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [seed]);
  const id = inventory?.id;
  const scanning = inventory ? active(inventory) : false;
  useEffect(() => {
    if (!id || !scanning) return;
    const abort = new AbortController();
    const poll = async () => {
      try {
        const item = await api<MaterialInventory>(`${base}/${id}`);
        if (abort.signal.aborted) return; setInventory(current => current?.id === id ? item : current); setPollError('');
        setList(previous => previous.map(summary => summary.id !== item.id ? summary : { ...summary, phase: item.phase, findings: item.findings.length, verified: item.findings.filter(f => f.review === 'VERIFIED').length }));
        if (!active(item)) return;
      } catch { if (!abort.signal.aborted) setPollError('Could not refresh scan progress. Retrying; your saved findings are retained.'); }
      if (!abort.signal.aborted) pollTimer.current = setTimeout(() => void poll(), 1500);
    };
    pollTimer.current = setTimeout(() => void poll(), 500);
    return () => { abort.abort(); if (pollTimer.current) clearTimeout(pollTimer.current); };
  }, [id, scanning]);
  const select = async (selected: string) => {
    const token = ++selection.current; setLoading(true); setError(''); setPollError(''); setInventory(null); setExpanded(null); setShowForm(false); setKind(''); setReview(''); setAccess(''); setText('');
    try { const item = await api<MaterialInventory>(`${base}/${selected}`); if (token === selection.current) setInventory(item); }
    catch (e) { if (token === selection.current) setError(e instanceof Error ? e.message : 'Could not load inventory.'); }
    finally { if (token === selection.current) setLoading(false); }
  };
  const create = async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault(); const paperUrls = urls.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
    if (!paperUrls.length || paperUrls.length > 10) { setError('Enter between one and ten paper URLs or DOIs, one per line.'); return; }
    setSubmitting(true); setError(''); setPollError('');
    try { const item = await api<MaterialInventory>(base, 'POST', { name: name.trim(), paperUrls }); setInventory(item); setExpanded(null); setShowForm(false); setKind(''); setReview(''); setAccess(''); setText(''); setList(await api<MaterialSummary[]>(base)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not start scan.'); }
    finally { setSubmitting(false); }
  };
  const save = async (findingId: string, decision: MaterialReview, notes: string) => {
    if (!inventory) return;
    const item = await api<MaterialInventory>(`${base}/${inventory.id}/findings/${findingId}`, 'PATCH', { decision, notes });
    setInventory(current => current?.id === item.id ? item : current); setList(await api<MaterialSummary[]>(base));
  };
  const download = async () => {
    if (!inventory) return; setExporting(true); setError('');
    try { const response = await fetch(`${base}/${inventory.id}/export.csv${exportScope ? `?review=${exportScope}` : ''}`); if (!response.ok) throw new Error('Could not export the inventory.'); const url = URL.createObjectURL(await response.blob()); const link = document.createElement('a'); link.href = url; link.download = `materials-${inventory.id}${exportScope ? '-verified' : ''}.csv`; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
    catch (e) { setError(e instanceof Error ? e.message : 'Export failed.'); } finally { setExporting(false); }
  };
  const findings = inventory?.findings ?? [];
  const verified = findings.filter(f => f.review === 'VERIFIED').length;
  const visible = findings.filter(f => (!kind || f.kind === kind) && (!review || f.review === review) && (!access || f.check.access === access) && `${f.label} ${f.url} ${f.notes}`.toLowerCase().includes(text.toLowerCase()));
  const form = <form onSubmit={create} className="space-y-4 rounded-2xl border border-teal-900 bg-slate-900/70 p-5">
    <div><h2 className="font-semibold">Start a materials scan</h2><p className="mt-1 text-xs leading-relaxed text-slate-400">Use HTML paper landing pages, including DOI links. This version does not extract links from PDFs or crawl project pages recursively.</p></div>
    <label className="block text-xs text-slate-400">Inventory name<input aria-label="Inventory name" required maxLength={200} className={`${field} mt-1`} value={name} onChange={e => setName(e.target.value)} /></label>
    <label className="block text-xs text-slate-400">Paper URLs or DOIs<textarea aria-label="Paper URLs or DOIs" required maxLength={20000} className={`${field} mt-1 min-h-28 font-mono`} value={urls} onChange={e => setUrls(e.target.value)} placeholder={'https://publisher.org/paper\n10.1234/example'} /></label>
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-xs text-slate-500">One per line · Up to 10 papers · 100 candidates · 20 resource checks</p><button className={primary} disabled={submitting || !name.trim() || !urls.trim()}>{submitting ? <LoaderCircle size={16} className="animate-spin" /> : <Search size={16} />}Find linked materials</button></div>
  </form>;
  return <div className="mx-auto grid max-w-screen-2xl gap-6 px-4 py-8 md:px-8 lg:grid-cols-[250px_minmax(0,1fr)]">
    <aside className="space-y-5"><div className="flex items-center justify-between"><h2 className="flex items-center gap-2 text-sm font-semibold text-slate-300"><Files size={17} />Materials inventories</h2><button className="rounded p-1 text-teal-300 hover:bg-slate-800 disabled:opacity-40" disabled={loading || submitting} aria-label="New inventory" onClick={() => setShowForm(!showForm)}><Plus size={20} /></button></div>
      <nav aria-label="Materials inventories" className="space-y-2">{list.map(i => <button key={i.id} disabled={loading || submitting} aria-current={inventory?.id === i.id ? 'page' : undefined} onClick={() => void select(i.id)} className={`w-full rounded-xl border p-3 text-left ${inventory?.id === i.id ? 'border-teal-700 bg-teal-950/40' : 'border-slate-800 hover:bg-slate-900'}`}><span className="block break-words text-sm font-medium">{i.name}</span><span className="mt-2 block text-xs text-slate-400">{i.findings} resources · {i.verified} verified · {i.phase.toLowerCase()}</span></button>)}</nav>
      <p className="text-xs leading-relaxed text-slate-500">A traceable inventory of the materials behind a paper. Automated discovery suggests candidates; you decide what is useful.</p>
    </aside>
    <main className="min-w-0 space-y-6">
      {error && <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950/50 p-4 text-sm text-rose-200">{error}</div>}
      {loading ? <p role="status" className="flex items-center gap-2 py-16 text-slate-400"><LoaderCircle size={18} className="animate-spin" />Loading materials inventories…</p> : <>
        {!inventory ? <header className="rounded-2xl border border-slate-800 bg-gradient-to-br from-teal-950/40 to-slate-950 p-7 md:p-10"><FlaskConical size={32} className="mb-5 text-teal-300" /><p className="text-xs font-semibold uppercase tracking-widest text-teal-300">From paper to research materials</p><h1 className="mt-3 max-w-2xl text-3xl font-semibold tracking-tight md:text-4xl">Find the materials behind the paper.</h1><p className="mt-4 max-w-2xl leading-relaxed text-slate-400">Discover linked datasets, code, supplements, protocols, and project pages. Keep the evidence, check access, and build an inventory you can use in your research.</p><div className="mt-6 flex flex-wrap items-center gap-3 text-xs text-slate-400"><span>Paper URL</span><ArrowRight size={14} /><span>Linked resources</span><ArrowRight size={14} /><span>Researcher verification</span><ArrowRight size={14} /><span>CSV report</span></div></header> : <header><p className="text-xs font-semibold uppercase tracking-widest text-teal-300">Materials inventory</p><h1 className="mt-2 break-words text-3xl font-semibold">{inventory.name}</h1><p className="mt-3 text-sm text-slate-400">{inventory.paperUrls.length} supplied paper pages · {inventory.phase.toLowerCase()} · Created {date(inventory.createdAt)}</p></header>}
        {(showForm || !inventory) && form}
        {inventory && <>
          <section aria-label="Materials summary" className="grid grid-cols-2 gap-3 md:grid-cols-4">{[{ label: 'Candidate resources', count: findings.length, icon: Files }, { label: 'HTTP responded', count: findings.filter(f => f.check.access === 'RESPONDED').length, icon: Link2 }, { label: 'Needs access review', count: findings.filter(f => f.check.access !== 'RESPONDED').length, icon: AlertTriangle }, { label: 'Researcher verified', count: verified, icon: ShieldCheck }].map(({ label, count, icon: Icon }) => <div key={label} className="rounded-xl border border-slate-800 bg-slate-900/60 p-4"><div className="flex items-center justify-between text-xs text-slate-400">{label}<Icon size={15} /></div><p className="mt-2 text-2xl font-semibold">{count}</p></div>)}</section>
          {scanning && <div role="status" className="flex items-center gap-3 rounded-lg border border-teal-900 bg-teal-950/40 p-4 text-sm text-teal-200"><LoaderCircle size={18} className="shrink-0 animate-spin" /><span>Scanning explicit links and checking destinations. {inventory.pages.length} of {inventory.paperUrls.length} paper pages processed. Results are saved as the scan progresses.</span></div>}
          {!scanning && inventory.pages.length > 0 && !inventory.pages.some(page => page.outcome.startsWith('HTML scanned')) && <div role="alert" className="rounded-lg border border-amber-800 bg-amber-950/40 p-4 text-sm text-amber-200">No paper pages could be scanned. This inventory cannot establish material availability. Review the page outcomes and network access.</div>}
          {pollError && <p role="alert" className="text-sm text-amber-300">{pollError}</p>}
          {!!inventory.warnings.length && <details className="rounded-lg border border-amber-900 bg-amber-950/20 p-4" open={inventory.phase === 'FAILED' || inventory.phase === 'INTERRUPTED'}><summary className="cursor-pointer text-sm text-amber-200">Scan coverage and limitations ({inventory.warnings.length})</summary><ul className="mt-3 space-y-2 text-xs leading-relaxed text-amber-200/80">{inventory.warnings.map((w, n) => <li key={n}>{w}</li>)}</ul></details>}
          <details className="rounded-xl border border-slate-800 p-4"><summary className="cursor-pointer text-sm font-medium">Paper page outcomes ({inventory.pages.length}/{inventory.paperUrls.length})</summary><ul className="mt-4 space-y-3">{inventory.pages.map((page, n) => <li key={`${page.requestedUrl}-${n}`} className="rounded-lg bg-slate-900 p-3 text-xs"><p className="mb-1 font-semibold text-slate-200">{page.title || 'Paper page'}</p><Link url={page.requestedUrl}>{page.requestedUrl}</Link><p className="mt-2 leading-relaxed text-slate-400">{page.outcome}</p><p className="mt-1 text-slate-500">HTTP {page.httpStatus ?? 'unknown'} · {page.candidates} candidate links</p></li>)}</ul></details>
          <section className="overflow-hidden rounded-2xl border border-slate-800">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 bg-slate-900/60 p-5"><h2 className="font-semibold">Linked materials <span className="ml-2 text-sm font-normal text-slate-400">{visible.length} of {findings.length}</span></h2><div className="flex items-center gap-2"><label className="sr-only" htmlFor="materials-export-scope">Materials export scope</label><select id="materials-export-scope" className="rounded border border-slate-700 bg-slate-950 px-2 py-2 text-xs" value={exportScope} onChange={e => setExportScope(e.target.value)}><option value="">All candidates</option><option value="VERIFIED">Verified only</option></select><button className={secondary} disabled={exporting || !findings.length || (exportScope === 'VERIFIED' && !verified)} onClick={() => void download()}><Download size={14} />Export inventory CSV</button></div></div>
            <div className="grid gap-3 border-b border-slate-800 p-4 md:grid-cols-2 xl:grid-cols-4"><input aria-label="Filter materials" className={field} value={text} onChange={e => setText(e.target.value)} placeholder="Filter links or notes" /><select aria-label="Filter material type" className={field} value={kind} onChange={e => setKind(e.target.value)}><option value="">All material types</option>{Object.entries(kinds).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select><select aria-label="Filter access result" className={field} value={access} onChange={e => setAccess(e.target.value)}><option value="">All access results</option>{Object.entries(accessLabels).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select><select aria-label="Filter resource review" className={field} value={review} onChange={e => setReview(e.target.value)}><option value="">All review decisions</option>{Object.entries(reviews).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></div>
            <div className="divide-y divide-slate-800">{visible.map(f => <article key={f.id}><button className="flex w-full items-start gap-3 p-5 text-left hover:bg-slate-900/60" aria-expanded={expanded === f.id} onClick={() => setExpanded(expanded === f.id ? null : f.id)}><ChevronRight size={18} className={`mt-1 shrink-0 text-slate-500 ${expanded === f.id ? 'rotate-90' : ''}`} /><div className="min-w-0 flex-1"><p className="font-medium">{f.label || f.url}</p><p className="mt-1 break-all text-xs text-slate-500">{f.url}</p><p className="mt-2 text-xs text-slate-400">Suggested type: {kinds[f.kind]} · {f.evidence.length} evidence links · {reviews[f.review]}</p></div><span className={`shrink-0 rounded-full px-2 py-1 text-xs ${f.check.access === 'RESPONDED' ? 'bg-teal-950 text-teal-300' : 'bg-amber-950 text-amber-200'}`}>{accessLabels[f.check.access]}</span></button>{expanded === f.id && <FindingReview finding={f} disabled={scanning} save={save} />}</article>)}</div>
            {!visible.length && <div className="p-10 text-center text-sm leading-relaxed text-slate-400">{findings.length ? 'No resources match these filters.' : scanning ? 'No candidates discovered yet. The scan is still running.' : 'No candidate materials were found on the pages successfully scanned. This does not establish that materials do not exist. Check page outcomes and inspect the papers manually.'}</div>}
          </section>
          <p className="text-xs leading-relaxed text-slate-500">Exports follow the export scope, independently of the filters above. During a scan, exports are snapshots. HTTP checks do not establish file contents, scientific relevance, licensing, or permanent availability.</p>
        </>}
      </>}
    </main>
  </div>;
}

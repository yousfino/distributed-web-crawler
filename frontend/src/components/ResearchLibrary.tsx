import { useEffect, useRef, useState } from 'react';
import { BookOpen, Search, Plus, Download, ExternalLink, Check, X, Clock, ChevronRight, LoaderCircle, Library, History } from 'lucide-react';
import type { Decision, Paper, ProjectSummary, ResearchProject, SearchRequest } from '../types/research';

const base = '/api/research/projects';
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message || `Request failed (${response.status}). Please try again.`);
  }
  return response.json() as Promise<T>;
}
const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const date = (value: string) => new Date(value).toLocaleString();
const decisionLabel: Record<Decision, string> = { UNDECIDED: 'Undecided', INCLUDED: 'Included', EXCLUDED: 'Excluded' };
const badge: Record<Decision, string> = {
  UNDECIDED: 'bg-slate-800 text-slate-300', INCLUDED: 'bg-emerald-950 text-emerald-300', EXCLUDED: 'bg-rose-950 text-rose-300',
};
const input = 'w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-teal-400 focus:outline-none';
const primary = 'inline-flex items-center justify-center gap-2 rounded-lg bg-teal-400 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-300 disabled:opacity-40';
const secondary = 'inline-flex items-center justify-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm hover:bg-slate-800 disabled:opacity-40';
function SafeLink({ url, children }: { url: string; children: React.ReactNode }) {
  if (!/^https?:\/\//i.test(url)) return null;
  return <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-teal-300 hover:underline">{children}<ExternalLink size={13} /></a>;
}

function PaperReview({ paper, save }: { paper: Paper; save: (paperId: string, decision: Decision, reason: string, notes: string) => Promise<void> }) {
  const [decision, setDecision] = useState(paper.decision);
  const [reason, setReason] = useState(paper.reason);
  const [notes, setNotes] = useState(paper.notes);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  return <div className="grid gap-6 border-t border-slate-800 bg-slate-950/60 p-5 lg:grid-cols-2">
    <div className="space-y-4 text-sm">
      <div className="flex flex-wrap gap-4"><SafeLink url={paper.url}>Publisher / record</SafeLink><SafeLink url={paper.openAccessUrl}>Open-access full text</SafeLink></div>
      <p className="text-xs text-slate-400">DOI: {paper.doi || 'Not supplied'} · Type: {paper.publicationType || 'Not supplied'}</p>
      <div><h4 className="mb-2 font-semibold">Abstract</h4><p className="whitespace-pre-line leading-relaxed text-slate-300">{paper.abstractText || 'This source did not supply an abstract. Open the source record to review the paper.'}</p></div>
      <details className="text-xs text-slate-400"><summary className="cursor-pointer text-teal-300">Source and search provenance ({paper.provenance.length})</summary>
        <ul className="mt-3 space-y-3">{paper.provenance.map((p, i) => <li key={`${p.searchId}-${p.source}-${i}`} className="break-words rounded border border-slate-800 p-3">
          <SafeLink url={p.sourceUrl}>{p.source}</SafeLink><p className="mt-1">Record: {p.sourceId}</p><p>Query: {p.query}</p><p>Retrieved: {date(p.retrievedAt)}</p><p>Search ID: {p.searchId}</p>
        </li>)}</ul>
      </details>
    </div>
    <form className="space-y-3" onSubmit={async event => {
      event.preventDefault(); setSaving(true); setError(''); setMessage('');
      try { await save(paper.id, decision, reason, notes); setMessage('Screening saved.'); }
      catch (e) { setError(e instanceof Error ? e.message : 'Could not save screening.'); }
      finally { setSaving(false); }
    }}>
      <h4 className="text-sm font-semibold">Screen this paper</h4>
      <label className="block text-xs text-slate-400">Decision<select aria-label="Decision" className={`${input} mt-1`} value={decision} onChange={e => { setDecision(e.target.value as Decision); setMessage(''); }}>
        {Object.entries(decisionLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="block text-xs text-slate-400">{decision === 'EXCLUDED' ? 'Exclusion reason (required)' : 'Screening reason'}
        <input className={`${input} mt-1`} value={reason} maxLength={2000} required={decision === 'EXCLUDED'} onChange={e => { setReason(e.target.value); setMessage(''); }} placeholder="e.g. Outside the population of interest" />
      </label>
      <label className="block text-xs text-slate-400">Research notes<textarea aria-label="Research notes" className={`${input} mt-1 min-h-24`} maxLength={10000} value={notes} onChange={e => { setNotes(e.target.value); setMessage(''); }} placeholder="Methods, findings, limitations, or points to revisit…" /></label>
      <button className={primary} disabled={saving}>{saving ? <LoaderCircle size={15} className="animate-spin" /> : <Check size={15} />}Save screening</button>
      {message && <p role="status" className="text-xs text-teal-300">{message}</p>}
      {error && <p role="alert" className="text-xs text-rose-300">{error}</p>}
    </form>
  </div>;
}

export function ResearchLibrary() {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [project, setProject] = useState<ResearchProject | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState('');
  const [question, setQuestion] = useState('');
  const [query, setQuery] = useState('');
  const [fromYear, setFromYear] = useState('');
  const [toYear, setToYear] = useState('');
  const [publicationType, setPublicationType] = useState('');
  const [sources, setSources] = useState(['openalex', 'crossref', 'pubmed']);
  const [filter, setFilter] = useState<Decision | ''>('');
  const [textFilter, setTextFilter] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [exportScope, setExportScope] = useState('');
  const [exporting, setExporting] = useState(false);
  const [searchMessage, setSearchMessage] = useState('');
  const selection = useRef(0);

  const refreshProjects = async () => setProjects(await request<ProjectSummary[]>(base));
  useEffect(() => {
    let active = true;
    void request<ProjectSummary[]>(base).then(async summaries => {
      if (!active) return;
      setProjects(summaries);
      if (summaries[0]) {
        const detail = await request<ResearchProject>(`${base}/${summaries[0].id}`);
        if (active) setProject(detail);
      }
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Could not load projects.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const selectProject = async (id: string) => {
    const token = ++selection.current;
    setLoading(true); setError(''); setExpanded(null); setSearchMessage(''); setProject(null); setFilter(''); setTextFilter('');
    try { const detail = await request<ResearchProject>(`${base}/${id}`); if (token === selection.current) setProject(detail); }
    catch (e) { if (token === selection.current) setError(e instanceof Error ? e.message : 'Could not load project.'); }
    finally { if (token === selection.current) setLoading(false); }
  };
  const createProject = async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const detail = await request<ResearchProject>(base, json('POST', { name, question }));
      setProject(detail); setShowCreate(false); setName(''); setQuestion(''); setExpanded(null); setSearchMessage(''); setFilter(''); setTextFilter('');
      await refreshProjects();
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not create project.'); }
    finally { setBusy(false); }
  };
  const search = async (event: React.SubmitEvent<HTMLFormElement>) => {
    event.preventDefault(); if (!project) return;
    if (fromYear && toYear && Number(fromYear) > Number(toYear)) { setError('Start year must not exceed end year.'); return; }
    setBusy(true); setError(''); setSearchMessage('');
    const body: SearchRequest = { query: query.trim(), fromYear: fromYear ? Number(fromYear) : null, toYear: toYear ? Number(toYear) : null, publicationType: publicationType || null, sources };
    try {
      const detail = await request<ResearchProject>(`${base}/${project.id}/searches`, json('POST', body));
      setProject(detail);
      const run = detail.searches.at(-1)!;
      setSearchMessage(`${run.added} new papers added from ${run.found} source records. Existing papers and screening decisions were preserved.`);
      await refreshProjects();
    } catch (e) { setError(e instanceof Error ? e.message : 'Search could not complete.'); }
    finally { setBusy(false); }
  };
  const saveScreening = async (paperId: string, decision: Decision, reason: string, notes: string) => {
    if (!project) return;
    const detail = await request<ResearchProject>(`${base}/${project.id}/papers/${paperId}`, json('PATCH', { decision, reason, notes }));
    // Do not replace another project if navigation changed during the save.
    setProject(current => current?.id === detail.id ? detail : current);
    await refreshProjects();
  };
  const download = async (format: 'csv' | 'bib') => {
    if (!project) return;
    setExporting(true); setError('');
    try {
      const response = await fetch(`${base}/${project.id}/export.${format}${exportScope ? `?decision=${exportScope}` : ''}`);
      if (!response.ok) throw new Error('Could not export this project. Please try again.');
      const blobUrl = URL.createObjectURL(await response.blob());
      const a = document.createElement('a'); a.href = blobUrl;
      a.download = `${project.name.replace(/[^a-z0-9-]/gi, '-').slice(0, 80) || 'research'}-${exportScope === 'INCLUDED' ? 'included' : 'all'}.${format}`;
      document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not export project.'); }
    finally { setExporting(false); }
  };
  const papers = project?.papers ?? [];
  const included = papers.filter(p => p.decision === 'INCLUDED').length;
  const excluded = papers.filter(p => p.decision === 'EXCLUDED').length;
  const visible = papers.filter(p => (!filter || p.decision === filter) && `${p.title} ${p.authors.join(' ')} ${p.doi}`.toLowerCase().includes(textFilter.toLowerCase()));
  const lastSearch = project?.searches.at(-1);

  return <div className="mx-auto grid max-w-screen-2xl gap-6 px-4 py-8 md:px-8 lg:grid-cols-[250px_minmax(0,1fr)]">
    <aside className="space-y-5">
      <div className="flex items-center justify-between"><h2 className="flex items-center gap-2 text-sm font-semibold text-slate-300"><Library size={17} />Research projects</h2>
        <button className="rounded p-1 text-teal-300 hover:bg-slate-800" aria-label="Create project" disabled={busy || loading} onClick={() => setShowCreate(!showCreate)}><Plus size={20} /></button></div>
      {showCreate && <form onSubmit={createProject} className="space-y-3 rounded-xl border border-teal-800 bg-slate-900 p-4">
        <label className="block text-xs text-slate-400">Project name<input className={`${input} mt-1`} value={name} maxLength={200} onChange={e => setName(e.target.value)} required placeholder="e.g. Urban heat and health" /></label>
        <label className="block text-xs text-slate-400">Research question<textarea className={`${input} mt-1 min-h-24`} value={question} maxLength={4000} onChange={e => setQuestion(e.target.value)} required placeholder="What question will this review answer?" /></label>
        <button className={`${primary} w-full`} disabled={busy || !name.trim() || !question.trim()}>Create project</button>
      </form>}
      <nav aria-label="Research projects" className="space-y-2">{projects.map(p => <button key={p.id} disabled={busy || loading} onClick={() => void selectProject(p.id)} aria-current={project?.id === p.id ? 'page' : undefined}
        className={`w-full rounded-xl border p-3 text-left transition ${project?.id === p.id ? 'border-teal-700 bg-teal-950/40' : 'border-slate-800 hover:bg-slate-900'}`}>
        <span className="block break-words text-sm font-medium">{p.name}</span><span className="mt-2 block text-xs text-slate-400">{p.papers} papers · {p.included} included</span>
      </button>)}</nav>
      <p className="text-xs leading-relaxed text-slate-500">Build a traceable collection of literature. Search records and screening decisions are saved with each project.</p>
    </aside>
    <main className="min-w-0 space-y-6">
      {error && <div role="alert" className="rounded-xl border border-rose-800 bg-rose-950/60 p-4 text-sm text-rose-200">{error}</div>}
      {loading ? <div role="status" className="flex items-center gap-2 py-16 text-slate-400"><LoaderCircle className="animate-spin" size={20} />Loading research library…</div> : !project ? <div className="rounded-2xl border border-dashed border-slate-700 p-10 text-center md:p-20">
        <BookOpen size={40} className="mx-auto mb-5 text-teal-300" /><h1 className="text-3xl font-semibold">A focused home for your literature</h1><p className="mx-auto mt-4 max-w-lg leading-relaxed text-slate-400">Start with a research question. Discover papers across scholarly sources, screen the evidence, and export a collection you can cite.</p>
        <button onClick={() => setShowCreate(true)} className={`${primary} mt-6`}><Plus size={16} />Create your first project</button>
      </div> : <>
        <header><p className="mb-2 text-xs font-semibold uppercase tracking-widest text-teal-300">Literature discovery & screening</p><h1 className="break-words text-3xl font-semibold tracking-tight">{project.name}</h1><p className="mt-3 max-w-3xl whitespace-pre-line leading-relaxed text-slate-400">{project.question}</p></header>
        <section aria-label="Project summary" className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {[{ label: 'Papers collected', count: papers.length, icon: BookOpen }, { label: 'To screen', count: papers.length - included - excluded, icon: Clock }, { label: 'Included', count: included, icon: Check }, { label: 'Excluded', count: excluded, icon: X }].map(({ label, count, icon: Icon }) =>
            <div key={label} className="rounded-xl border border-slate-800 bg-slate-900/60 p-4"><div className="flex items-center justify-between text-xs text-slate-400">{label}<Icon size={15} /></div><p className="mt-2 text-2xl font-semibold">{count}</p></div>)}
        </section>
        <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
          <h2 className="mb-4 flex items-center gap-2 font-semibold"><Search size={18} className="text-teal-300" />Discover literature</h2>
          <p className="mb-4 text-xs leading-relaxed text-slate-400">Google Scholar uses this application's own adapter, without a third-party search API. Access can be blocked; it is never guaranteed. <SafeLink url={`https://scholar.google.com/scholar?hl=en&q=${encodeURIComponent(query)}${fromYear ? `&as_ylo=${encodeURIComponent(fromYear)}` : ''}${toYear ? `&as_yhi=${encodeURIComponent(toYear)}` : ''}`}>Open Scholar manually</SafeLink></p>
          <form onSubmit={search} className="space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row"><label className="flex-1"><span className="sr-only">Search terms</span><input className={input} required maxLength={500} value={query} onChange={e => setQuery(e.target.value)} placeholder="Search a topic, paper title, or DOI…" /></label><button className={primary} disabled={busy || !query.trim() || !sources.length}>{busy ? <LoaderCircle size={16} className="animate-spin" /> : <Search size={16} />}{busy ? 'Searching…' : 'Search papers'}</button></div>
            <div className="grid gap-3 sm:grid-cols-3">
              <label className="text-xs text-slate-400">From year<input type="number" min="1600" max="2200" className={`${input} mt-1`} placeholder="Any year" value={fromYear} onChange={e => setFromYear(e.target.value)} /></label>
              <label className="text-xs text-slate-400">To year<input type="number" min="1600" max="2200" className={`${input} mt-1`} placeholder="Any year" value={toYear} onChange={e => setToYear(e.target.value)} /></label>
              <label className="text-xs text-slate-400">Publication type<select aria-label="Publication type" className={`${input} mt-1`} value={publicationType} onChange={e => setPublicationType(e.target.value)}><option value="">Any type</option><option value="article">Article</option><option value="preprint">Preprint</option><option value="book-chapter">Book chapter</option></select></label>
            </div>
            <fieldset className="flex flex-wrap items-center gap-4 text-sm"><legend className="mb-2 text-xs text-slate-400">Scholarly sources</legend>{['openalex', 'crossref', 'pubmed', 'scholar'].map(source => <label key={source} className="flex items-center gap-2"><input type="checkbox" checked={sources.includes(source)} onChange={e => setSources(previous => e.target.checked ? [...previous, source] : previous.filter(s => s !== source))} />{{ openalex: 'OpenAlex', crossref: 'Crossref', pubmed: 'PubMed', scholar: 'Google Scholar (best effort)' }[source]}</label>)}</fieldset>
            <p className="text-xs text-slate-500">Up to 25 records per API source; up to 10 for Google Scholar. Results are deduplicated; this is a discovery aid, not an exhaustive systematic search.</p>
          </form>
          {busy && <p role="status" className="mt-4 text-sm text-teal-300">Retrieving source records. This may take up to two minutes.</p>}
          {searchMessage && <p role="status" className="mt-4 text-sm text-teal-300">{searchMessage}</p>}
          {!!lastSearch?.warnings.length && <div role="alert" className="mt-4 rounded-lg border border-amber-800 bg-amber-950/40 p-3 text-sm text-amber-200"><p className="mb-1 font-semibold">Some sources could not be searched</p>{lastSearch.warnings.map(w => <p key={w}>{w}</p>)}<p className="mt-2 text-xs">Available results are saved. Retry the search after access is restored.</p></div>}
        </section>
        <section className="overflow-hidden rounded-2xl border border-slate-800">
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 bg-slate-900/60 p-5"><h2 className="font-semibold">Paper library <span className="ml-2 text-sm font-normal text-slate-400">{visible.length} of {papers.length}</span></h2>
            <div className="flex flex-wrap items-center gap-2"><label className="sr-only" htmlFor="export-scope">Export scope</label><select id="export-scope" className="rounded border border-slate-700 bg-slate-950 px-2 py-2 text-xs" value={exportScope} onChange={e => setExportScope(e.target.value)}><option value="">All papers</option><option value="INCLUDED">Included only</option></select>
              <button className={secondary} disabled={exporting || !papers.length || (exportScope === 'INCLUDED' && !included)} onClick={() => void download('csv')}><Download size={14} />CSV</button><button className={secondary} disabled={exporting || !papers.length || (exportScope === 'INCLUDED' && !included)} onClick={() => void download('bib')}>BibTeX</button></div>
          </div>
          <div className="flex flex-wrap gap-3 border-b border-slate-800 p-4"><label className="min-w-48 flex-1"><span className="sr-only">Filter collected papers</span><input className={input} value={textFilter} onChange={e => setTextFilter(e.target.value)} placeholder="Filter by title, author, or DOI" /></label><label><span className="sr-only">Filter by decision</span><select className={input} value={filter} onChange={e => setFilter(e.target.value as Decision | '')}><option value="">All decisions</option>{Object.entries(decisionLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
          <div className="divide-y divide-slate-800">{visible.map(p => <article key={p.id}>
            <button className="flex w-full items-start gap-3 p-5 text-left hover:bg-slate-900/70" aria-expanded={expanded === p.id} onClick={() => setExpanded(expanded === p.id ? null : p.id)}>
              <ChevronRight size={18} className={`mt-1 shrink-0 text-slate-500 transition ${expanded === p.id ? 'rotate-90' : ''}`} />
              <div className="min-w-0 flex-1"><h3 className="font-medium leading-relaxed text-slate-100">{p.title}</h3><p className="mt-1 text-sm text-slate-400">{p.authors.join('; ') || 'Authors not supplied'}</p><p className="mt-2 text-xs text-slate-500">{p.year ?? 'Year unknown'} · {p.venue || 'Venue not supplied'}{p.openAccessUrl ? ' · Open access available' : ''}</p></div>
              <span className={`shrink-0 rounded-full px-2 py-1 text-xs ${badge[p.decision]}`}>{decisionLabel[p.decision]}</span>
            </button>
            {expanded === p.id && <PaperReview paper={p} save={saveScreening} />}
          </article>)}</div>
          {!visible.length && <div className="p-10 text-center text-sm text-slate-400">{papers.length ? 'No papers match these filters.' : 'No papers collected yet. Search scholarly sources to begin.'}</div>}
        </section>
        <details className="rounded-xl border border-slate-800 p-5"><summary className="cursor-pointer text-sm font-medium"><History size={16} className="mr-2 inline" />Search history ({project.searches.length})</summary><p className="mt-3 text-xs text-slate-500">Every search records its terms, filters, source selection, retrieval time, and outcome. Times below use your browser timezone; exports use UTC.</p><ul className="mt-4 space-y-3">{[...project.searches].reverse().map(run => <li key={run.id} className="rounded-lg bg-slate-900 p-3 text-sm"><p className="font-medium">{run.request.query}</p><p className="mt-1 text-xs text-slate-400">{date(run.searchedAt)} · {run.request.sources.join(', ')} · {run.request.fromYear ?? 'Any'}–{run.request.toYear ?? 'Any'} · {run.request.publicationType || 'Any type'}</p><p className="mt-1 text-xs text-slate-400">{run.found} source records · {run.added} added · {run.warnings.length ? 'Source errors recorded' : 'Search completed'}</p><p className="mt-1 break-all text-xs text-slate-500">Search ID: {run.id}</p></li>)}</ul></details>
      </>}
    </main>
  </div>;
}

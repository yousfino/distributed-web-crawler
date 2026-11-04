import React, { useState, useEffect, useCallback, useRef } from "react";
import { Play, Square, Activity, Globe, ShieldAlert, Cpu, Download, Bell } from "lucide-react";
import { useCrawlerSocket } from "./hooks/useCrawlerSocket";
import type { CrawlProgressEvent, CrawlStats } from "./types/crawler";
import { NetworkGraph } from "./components/NetworkGraph";

export const App: React.FC = () => {
  const [seedUrl, setSeedUrl] = useState("https://crawler-test.com");
  const [events, setEvents] = useState<CrawlProgressEvent[]>([]);
  const [edges, setEdges] = useState<{ source: string; target: string }[]>([]);
  const [stats, setStats] = useState<CrawlStats>({
    jobId: null,
    isRunning: false,
    totalPagesCrawled: 0,
    graphNodesCount: 0,
    graphEdgesCount: 0,
    hasGraphCycle: false,
    topKeywords: [],
    discoveredUris: [],
  });

  const [notifyOnComplete, setNotifyOnComplete] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [exporting, setExporting] = useState(false);
  const notificationEnabled = useRef(false);
  const activeJob = useRef<string | null>(null);
  const stoppedJob = useRef<string | null>(null);
  const statsRequest = useRef(0);

  const fetchStats = async () => {
    const requestId = ++statsRequest.current;
    try {
      const res = await fetch("/api/crawler/stats?topK=6");
      if (res.ok) {
        const data: CrawlStats = await res.json();
        if (requestId !== statsRequest.current) return;
        setStats(data);
        if (data.isRunning) activeJob.current = data.jobId;
        if (!data.isRunning && data.jobId && activeJob.current === data.jobId) {
          activeJob.current = null;
          const stopped = stoppedJob.current === data.jobId;
          if (notificationEnabled.current) {
            const message = `Crawl ${stopped ? "stopped" : "finished"}: ${data.totalPagesCrawled} pages processed. Your CSV report is ready.`;
            setNotice(message);
            if ("Notification" in window && Notification.permission === "granted") {
              try { new Notification(stopped ? "Crawl stopped" : "Crawl finished", { body: message, tag: data.jobId }); }
              catch { /* The in-app notification remains available. */ }
            }
          }
        }
      }
    } catch (err) {
      console.error("Failed to poll crawl statistics", err);
    }
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 2000);
    return () => clearInterval(interval);
  }, []);

  const handleSocketProgress = useCallback((event: CrawlProgressEvent) => {
    setEvents((prev) => [event, ...prev.slice(0, 49)]); // Keep last 50 events
  }, []);

  const { isConnected } = useCrawlerSocket(handleSocketProgress);

  const toggleNotifications = async (enabled: boolean) => {
    setNotifyOnComplete(enabled);
    notificationEnabled.current = enabled;
    if (enabled && "Notification" in window) {
      try {
        const permission = await Notification.requestPermission();
        if (permission !== "granted") setNotice("Completion alerts will appear here. Browser notifications are blocked.");
      } catch { setNotice("Completion alerts will appear here while this page is open."); }
    } else if (enabled) setNotice("Completion alerts will appear here while this page is open.");
  };

  const startCrawl = async () => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const response = await fetch("/api/crawler/start", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ seedUrl }),
      });
      if (!response.ok) throw new Error("Could not start the crawl. Check the URL and try again.");
      const result: { jobId: string } = await response.json();
      ++statsRequest.current; // Ignore polls started before the new job was accepted.
      activeJob.current = result.jobId;
      stoppedJob.current = null;
      setEvents([]);
      setEdges([]);
      await fetchStats();
    } catch (err) { setError(err instanceof Error ? err.message : "Could not start crawl."); }
    finally { setBusy(false); }
  };

  const stopCrawl = async () => {
    setBusy(true);
    setError("");
    stoppedJob.current = stats.jobId;
    try {
      const response = await fetch("/api/crawler/stop", { method: "POST" });
      if (!response.ok) throw new Error("Could not stop the crawl. Please try again.");
      await fetchStats();
    } catch (err) {
      stoppedJob.current = null;
      setError(err instanceof Error ? err.message : "Could not stop crawl.");
    } finally { setBusy(false); }
  };

  const exportCsv = async () => {
    setExporting(true);
    setError("");
    try {
      const response = await fetch("/api/crawler/export");
      if (!response.ok) throw new Error("Could not export the report. Please try again.");
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url;
      link.download = `crawl-report-${stats.jobId ?? "results"}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) { setError(err instanceof Error ? err.message : "Could not export report."); }
    finally { setExporting(false); }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Top Header */}
        <header className="flex flex-col md:flex-row md:items-center justify-between pb-6 border-b border-slate-800 gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
              <Globe className="text-sky-400 w-7 h-7" />
              Distributed Web Crawler & Analysis Platform
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Multithreaded traversal, $O(1)$ visited verification, and
              real-time link topology
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border ${
                isConnected
                  ? "bg-emerald-950/80 border-emerald-800 text-emerald-400"
                  : "bg-rose-950/80 border-rose-800 text-rose-400"
              }`}
            >
              <span
                className={`w-2 h-2 rounded-full ${isConnected ? "bg-emerald-400" : "bg-rose-400"}`}
              />
              {isConnected ? "WebSocket Active" : "Disconnected"}
            </span>
          </div>
        </header>

        {/* Control Bar */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex flex-col md:flex-row gap-4 items-center justify-between">
          <div className="flex-1 w-full flex gap-3">
            <input
              type="url"
              value={seedUrl}
              onChange={(e) => setSeedUrl(e.target.value)}
              placeholder="https://example.com"
              className="flex-1 bg-slate-950 border border-slate-700 rounded-lg px-4 py-2 text-sm text-white focus:outline-none focus:border-sky-500"
            />
            {stats.isRunning ? (
              <button
                disabled={busy}
                onClick={stopCrawl}
                className="bg-rose-600 hover:bg-rose-500 text-white font-medium text-sm px-5 py-2 rounded-lg flex items-center gap-2 transition"
              >
                <Square className="w-4 h-4" /> Stop
              </button>
            ) : (
              <button
                disabled={busy || !seedUrl.trim()}
                onClick={startCrawl}
                className="bg-sky-600 hover:bg-sky-500 text-white font-medium text-sm px-5 py-2 rounded-lg flex items-center gap-2 transition"
              >
                <Play className="w-4 h-4" /> Start Crawl
              </button>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input type="checkbox" checked={notifyOnComplete}
              onChange={(event) => void toggleNotifications(event.target.checked)} />
            <Bell className="w-4 h-4" /> Notify me when crawling ends
            <span className="text-xs text-slate-500">Keep this page open</span>
          </label>
          <div className="flex items-center gap-3">
            <span className="text-xs text-slate-400">All processed pages • {stats.isRunning ? "Snapshot of results so far" : "Ready for reporting"}</span>
            <button onClick={exportCsv} disabled={exporting || stats.totalPagesCrawled === 0}
              className="flex items-center gap-2 rounded-lg bg-slate-800 px-4 py-2 text-sm hover:bg-slate-700 disabled:opacity-40">
              <Download className="w-4 h-4" /> {exporting ? "Exporting…" : "Export CSV"}
            </button>
          </div>
        </div>
        {notice && <div role="status" className="rounded-lg border border-emerald-800 bg-emerald-950 p-3 text-sm text-emerald-200">{notice}</div>}
        {error && <div role="alert" className="rounded-lg border border-rose-800 bg-rose-950 p-3 text-sm text-rose-200">{error}</div>}

        {/* Metrics Overview Cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
            <div className="flex items-center justify-between text-slate-400 text-xs">
              <span>Pages Processed</span>
              <Activity className="w-4 h-4 text-sky-400" />
            </div>
            <div className="text-2xl font-bold mt-2 text-white">
              {stats.totalPagesCrawled}
            </div>
          </div>
          <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
            <div className="flex items-center justify-between text-slate-400 text-xs">
              <span>Graph Vertices / Edges</span>
              <Cpu className="w-4 h-4 text-indigo-400" />
            </div>
            <div className="text-2xl font-bold mt-2 text-white">
              {stats.graphNodesCount}{" "}
              <span className="text-xs text-slate-400 font-normal">
                / {stats.graphEdgesCount} edges
              </span>
            </div>
          </div>
          <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
            <div className="flex items-center justify-between text-slate-400 text-xs">
              <span>Cycle Detected</span>
              <ShieldAlert
                className={`w-4 h-4 ${stats.hasGraphCycle ? "text-amber-400" : "text-slate-500"}`}
              />
            </div>
            <div
              className={`text-2xl font-bold mt-2 ${stats.hasGraphCycle ? "text-amber-400" : "text-slate-400"}`}
            >
              {stats.hasGraphCycle ? "Yes (Cycles Found)" : "DAG Clean"}
            </div>
          </div>
          <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
            <div className="flex items-center justify-between text-slate-400 text-xs">
              <span>Top Vocabulary Keyword</span>
              <Globe className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-2xl font-bold mt-2 text-white truncate">
              {stats.topKeywords[0]?.keyword || "N/A"}
            </div>
          </div>
        </div>

        {/* Middle Visualizer & Keywords */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <NetworkGraph nodes={stats.discoveredUris} edges={edges} />
          </div>

          {/* Top-K Min-Heap Display */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
            <div>
              <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
                Top-K Frequent Terms (Min-Heap)
              </h3>
              <p className="text-xs text-slate-500 mt-1 mb-4">
                Calculated in $O(N \log K)$ across crawled DOM text
              </p>
              <div className="space-y-2">
                {stats.topKeywords.map((kw, idx) => (
                  <div
                    key={kw.keyword}
                    className="flex items-center justify-between p-2 bg-slate-950/60 rounded border border-slate-800/80 text-xs"
                  >
                    <span className="font-mono text-sky-300">
                      #{idx + 1} {kw.keyword}
                    </span>
                    <span className="font-semibold text-slate-400">
                      {kw.frequency} occ
                    </span>
                  </div>
                ))}
                {stats.topKeywords.length === 0 && (
                  <div className="text-xs text-slate-500 text-center py-6">
                    No keyword data collected yet
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Live WebSocket Event Stream Table */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 flex justify-between items-center">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
              Live Frontier Processing Logs
            </h3>
            <span className="text-xs text-slate-500">
              Streaming via STOMP /topic/progress
            </span>
          </div>
          <div className="overflow-x-auto max-h-72">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-950 text-slate-400 border-b border-slate-800">
                  <th className="p-3">Status</th>
                  <th className="p-3">URL</th>
                  <th className="p-3">Title</th>
                  <th className="p-3">Latency</th>
                  <th className="p-3">Words</th>
                  <th className="p-3">Discovered</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {events.map((ev, i) => (
                  <tr key={i} className="hover:bg-slate-800/40 font-mono">
                    <td className="p-3">
                      <span
                        className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                          ev.statusCode < 300
                            ? "bg-emerald-950 text-emerald-400"
                            : "bg-amber-950 text-amber-400"
                        }`}
                      >
                        {ev.statusCode}
                      </span>
                    </td>
                    <td className="p-3 text-sky-400 truncate max-w-xs">
                      {ev.pageUri}
                    </td>
                    <td className="p-3 text-slate-300 truncate max-w-xs">
                      {ev.title || "-"}
                    </td>
                    <td className="p-3 text-slate-400">{ev.latencyMs} ms</td>
                    <td className="p-3 text-slate-400">{ev.wordCount}</td>
                    <td className="p-3 text-slate-400">
                      +{ev.totalDiscoveredLinks} links
                    </td>
                  </tr>
                ))}
                {events.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-center p-6 text-slate-500">
                      No crawl logs. Launch a crawl target to view live worker
                      activity.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default App;

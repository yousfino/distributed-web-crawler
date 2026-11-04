import React, { useState, useEffect, useCallback } from "react";
import { Play, Square, Activity, Globe, ShieldAlert, Cpu } from "lucide-react";
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

  const fetchStats = async () => {
    try {
      const res = await fetch("/api/crawler/stats?topK=6");
      if (res.ok) {
        const data: CrawlStats = await res.json();
        setStats(data);
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

  const startCrawl = async () => {
    setEvents([]);
    setEdges([]);
    await fetch("/api/crawler/start", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ seedUrl }),
    });
    fetchStats();
  };

  const stopCrawl = async () => {
    await fetch("/api/crawler/stop", { method: "POST" });
    fetchStats();
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
                onClick={stopCrawl}
                className="bg-rose-600 hover:bg-rose-500 text-white font-medium text-sm px-5 py-2 rounded-lg flex items-center gap-2 transition"
              >
                <Square className="w-4 h-4" /> Stop
              </button>
            ) : (
              <button
                onClick={startCrawl}
                className="bg-sky-600 hover:bg-sky-500 text-white font-medium text-sm px-5 py-2 rounded-lg flex items-center gap-2 transition"
              >
                <Play className="w-4 h-4" /> Start Crawl
              </button>
            )}
          </div>
        </div>

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

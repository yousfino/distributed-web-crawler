export interface CrawlProgressEvent {
  jobId: string;
  pageUri: string;
  title: string;
  statusCode: number;
  wordCount: number;
  totalDiscoveredLinks: number;
  latencyMs: number;
  timestamp: string;
}

export interface KeywordRank {
  keyword: string;
  frequency: number;
}

export interface CrawlStats {
  jobId: string | null;
  isRunning: boolean;
  totalPagesCrawled: number;
  graphNodesCount: number;
  graphEdgesCount: number;
  hasGraphCycle: boolean;
  topKeywords: KeywordRank[];
  discoveredUris: string[];
}

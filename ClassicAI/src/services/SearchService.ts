export type SearchResult = {
  title: string;
  url: string;
  snippet: string;
};

const SEARCH_KEYWORDS = [
  'خبر', 'سعر', 'قانون', 'طب', 'صحة', 'تقنية', 'منتج', 'شركة', 'شخص',
  'إحصائية', 'إحصاء', 'حديث', 'أخبار', 'اليوم', 'الآن', 'تحديث',
  'news', 'price', 'law', 'medical', 'health', 'tech', 'product', 'company',
  'person', 'statistic', 'current', 'latest', 'today', 'now', 'update',
  'weather', 'stock', 'market', 'election', 'score', 'rate',
];

export class SearchService {
  static shouldSearch(query: string): boolean {
    const lower = query.toLowerCase();
    return SEARCH_KEYWORDS.some(k => lower.includes(k));
  }

  static async standardSearch(query: string): Promise<SearchResult[]> {
    await new Promise(r => setTimeout(r, 800));
    return [
      {
        title: `Search results for: ${query}`,
        url: 'https://example.com/1',
        snippet: `Brief information about "${query}" from reliable sources.`,
      },
      {
        title: 'Additional information',
        url: 'https://example.com/2',
        snippet: 'Additional relevant details on the topic.',
      },
    ];
  }

  static async extendedSearch(query: string): Promise<SearchResult[]> {
    await new Promise(r => setTimeout(r, 1500));
    return [
      {
        title: `Extended search: ${query}`,
        url: 'https://example.com/1',
        snippet: `Comprehensive analysis of "${query}".`,
      },
      {
        title: 'First additional source',
        url: 'https://example.com/2',
        snippet: 'Supplementary information from a second reliable source.',
      },
      {
        title: 'Second additional source',
        url: 'https://example.com/3',
        snippet: 'Related data and statistics on the topic.',
      },
    ];
  }

  static formatResultsForAI(results: SearchResult[]): string {
    return results.map((r, i) =>
      `[${i + 1}] ${r.title}\nURL: ${r.url}\n${r.snippet}`
    ).join('\n\n');
  }
}

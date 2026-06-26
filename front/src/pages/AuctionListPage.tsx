import { useState, useEffect, useCallback } from 'react'
import { searchAuctions, getPopularAuctions, getCategories, type SearchParams } from '../api/auction'
import AuctionCard from '../components/auction/AuctionCard'
import LoadingSpinner from '../components/common/LoadingSpinner'
import type { AuctionSummary, Category } from '../types'

const AuctionListPage = () => {
  const [auctions, setAuctions] = useState<AuctionSummary[]>([])
  const [popular, setPopular] = useState<AuctionSummary[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [cursor, setCursor] = useState<string | undefined>()
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [sort, setSort] = useState<SearchParams['sort']>('END_TIME')

  const fetchAuctions = useCallback(async (params: SearchParams, append = false) => {
    try {
      const res = await searchAuctions(params)
      setAuctions((prev) => append ? [...prev, ...res.content] : res.content)
      setCursor(res.nextCursor ?? undefined)
      setHasNext(res.hasNext)
    } catch {
      // keep existing data on error
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    Promise.all([
      fetchAuctions({ sort, categoryId, size: 20 }),
      getPopularAuctions(6).then(setPopular).catch(() => {}),
      getCategories().then(setCategories).catch(() => {}),
    ]).finally(() => setLoading(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    await fetchAuctions({ keyword: keyword || undefined, categoryId, sort, size: 20 })
    setLoading(false)
  }

  const handleLoadMore = async () => {
    if (!hasNext || !cursor) return
    setLoadingMore(true)
    await fetchAuctions({ keyword: keyword || undefined, categoryId, sort, cursor, size: 20 }, true)
    setLoadingMore(false)
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-6">
      {/* 검색 */}
      <form onSubmit={handleSearch} className="flex gap-2 mb-6">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="검색어를 입력하세요"
          className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <select
          value={categoryId ?? ''}
          onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : undefined)}
          className="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none"
        >
          <option value="">전체 카테고리</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <select
          value={sort}
          onChange={(e) => setSort(e.target.value as SearchParams['sort'])}
          className="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none"
        >
          <option value="END_TIME">마감 임박순</option>
          <option value="BID_COUNT">입찰 많은순</option>
          <option value="PRICE">가격순</option>
        </select>
        <button type="submit" className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700">검색</button>
      </form>

      {/* 인기 경매 */}
      {popular.length > 0 && (
        <section className="mb-8">
          <h2 className="text-base font-semibold text-gray-700 mb-3">🔥 인기 경매</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
            {popular.map((a) => <AuctionCard key={a.auctionId} auction={a} />)}
          </div>
        </section>
      )}

      {/* 전체 목록 */}
      <section>
        <h2 className="text-base font-semibold text-gray-700 mb-3">경매 목록</h2>
        {loading ? (
          <LoadingSpinner />
        ) : auctions.length === 0 ? (
          <p className="text-center text-gray-400 py-12">검색 결과가 없습니다.</p>
        ) : (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              {auctions.map((a) => <AuctionCard key={a.auctionId} auction={a} />)}
            </div>
            {hasNext && (
              <div className="text-center mt-6">
                <button
                  onClick={handleLoadMore}
                  disabled={loadingMore}
                  className="px-6 py-2 border border-gray-300 text-sm text-gray-600 rounded hover:bg-gray-50 disabled:opacity-50"
                >
                  {loadingMore ? '불러오는 중...' : '더 보기'}
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  )
}

export default AuctionListPage
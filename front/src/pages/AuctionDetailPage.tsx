import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAuction, getSimilarAuctions } from '../api/auction'
import { getAuctionBids } from '../api/bid'
import { useAuthStore } from '../store/authStore'
import { useStomp } from '../hooks/useStomp'
import BidForm from '../components/auction/BidForm'
import BidHistory from '../components/auction/BidHistory'
import CountdownTimer from '../components/auction/CountdownTimer'
import AuctionCard from '../components/auction/AuctionCard'
import LoadingSpinner from '../components/common/LoadingSpinner'
import type { AuctionDetail, BidHistoryItem, AuctionSummary, WsMessage } from '../types'

const formatPrice = (p: number) => p.toLocaleString('ko-KR') + '원'

const conditionLabel: Record<string, string> = {
  NEW: '새 상품', LIKE_NEW: '거의 새 것', GOOD: '상태 좋음', FAIR: '보통', POOR: '상태 나쁨',
}

const AuctionDetailPage = () => {
  const { id } = useParams<{ id: string }>()
  const auctionId = Number(id)
  const navigate = useNavigate()
  const { member } = useAuthStore()

  const [auction, setAuction] = useState<AuctionDetail | null>(null)
  const [bids, setBids] = useState<BidHistoryItem[]>([])
  const [similar, setSimilar] = useState<AuctionSummary[]>([])
  const [currentPrice, setCurrentPrice] = useState(0)
  const [minimumBidAmount, setMinimumBidAmount] = useState(0)
  const [bidCount, setBidCount] = useState(0)
  const [endTime, setEndTime] = useState('')
  const [winnerId, setWinnerId] = useState<number | null>(null)
  const [selectedImage, setSelectedImage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadBids = useCallback(async () => {
    const res = await getAuctionBids(auctionId)
    setBids(res.content)
  }, [auctionId])

  useEffect(() => {
    const load = async () => {
      try {
        const [a, b, s] = await Promise.all([
          getAuction(auctionId),
          getAuctionBids(auctionId),
          getSimilarAuctions(auctionId).catch(() => []),
        ])
        setAuction(a)
        setCurrentPrice(a.currentPrice)
        setMinimumBidAmount(a.minimumBidAmount)
        setBidCount(a.bidCount)
        setEndTime(a.endTime)
        setWinnerId(a.winnerId)
        setBids(b.content)
        setSimilar(s)
      } catch {
        setError('경매 정보를 불러올 수 없습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [auctionId])

  const handleWsMessage = useCallback((msg: WsMessage) => {
    if (msg.type === 'NEW_BID') {
      if (msg.currentPrice !== undefined) setCurrentPrice(msg.currentPrice)
      if (msg.bidCount !== undefined) setBidCount(msg.bidCount)
      loadBids()
      getAuction(auctionId).then(a => {
        setWinnerId(a.winnerId)
        setMinimumBidAmount(a.minimumBidAmount)
      }).catch(() => {})
    } else if (msg.type === 'COUNTDOWN_EXTENDED' && msg.newEndTime) {
      setEndTime(msg.newEndTime)
    } else if (msg.type === 'AUCTION_ENDED') {
      setAuction((prev) => prev ? { ...prev, status: 'ENDED' } : prev)
    } else if (msg.type === 'AUCTION_SOLD') {
      setAuction((prev) => prev ? { ...prev, status: 'SOLD' } : prev)
    }
  }, [loadBids, auctionId])

  useStomp(auctionId, handleWsMessage)

  if (loading) return <LoadingSpinner />
  if (error || !auction) return (
    <div className="max-w-4xl mx-auto px-4 py-12 text-center">
      <p className="text-gray-500">{error ?? '경매를 찾을 수 없습니다.'}</p>
      <button onClick={() => navigate('/')} className="mt-4 text-blue-600 text-sm hover:underline">목록으로</button>
    </div>
  )

  const isMySelling = member?.memberId === auction.seller.memberId
  const isHighestBidder = winnerId !== null && member?.memberId === winnerId

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* 이미지 */}
        <div>
          <div className="w-full aspect-square bg-gray-100 rounded-lg overflow-hidden flex items-center justify-center">
            {auction.imageUrls.length > 0 ? (
              <img src={auction.imageUrls[selectedImage]} alt={auction.title} className="w-full h-full object-cover" />
            ) : (
              <span className="text-gray-300 text-6xl">📦</span>
            )}
          </div>
          {auction.imageUrls.length > 1 && (
            <div className="flex gap-2 mt-2">
              {auction.imageUrls.map((url, i) => (
                <button key={i} onClick={() => setSelectedImage(i)}
                  className={`w-14 h-14 rounded border-2 overflow-hidden ${selectedImage === i ? 'border-blue-500' : 'border-transparent'}`}>
                  <img src={url} alt="" className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* 정보 */}
        <div className="space-y-4">
          <div>
            <p className="text-xs text-gray-400">{auction.category.name} · {conditionLabel[auction.condition]}</p>
            <h1 className="text-xl font-bold text-gray-800 mt-1">{auction.title}</h1>
          </div>

          <div className="bg-gray-50 rounded-lg p-4 space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-500">현재가</span>
              <span className="text-2xl font-bold text-blue-600">{formatPrice(currentPrice)}</span>
            </div>
            {auction.buyNowPrice && (
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-500">즉시 구매가</span>
                <span className="text-base font-semibold text-orange-500">{formatPrice(auction.buyNowPrice)}</span>
              </div>
            )}
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-500">입찰 수</span>
              <span className="text-sm font-medium text-gray-700">{bidCount}회</span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-500">남은 시간</span>
              <CountdownTimer endTime={endTime} />
            </div>
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-500">판매자</span>
              <span className="text-sm text-gray-700">{auction.seller.nickname}</span>
            </div>
          </div>

          <BidForm
            auctionId={auctionId}
            currentPrice={currentPrice}
            minimumBidAmount={minimumBidAmount}
            buyNowPrice={auction.buyNowPrice}
            status={auction.status}
            isMySelling={isMySelling}
            isHighestBidder={isHighestBidder}
            onBidSuccess={() => {
              loadBids()
              getAuction(auctionId).then(a => {
                setWinnerId(a.winnerId)
                setMinimumBidAmount(a.minimumBidAmount)
              }).catch(() => {})
            }}
          />
        </div>
      </div>

      {/* 설명 */}
      <div className="mt-8">
        <h2 className="text-base font-semibold text-gray-700 mb-2">상품 설명</h2>
        <p className="text-sm text-gray-600 whitespace-pre-line bg-gray-50 rounded-lg p-4">{auction.description}</p>
      </div>

      {/* 입찰 내역 */}
      <div className="mt-8">
        <h2 className="text-base font-semibold text-gray-700 mb-2">입찰 내역</h2>
        <BidHistory bids={bids} />
      </div>

      {/* 유사 상품 */}
      {similar.length > 0 && (
        <div className="mt-8">
          <h2 className="text-base font-semibold text-gray-700 mb-3">유사 상품 추천</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
            {similar.map((a) => <AuctionCard key={a.auctionId} auction={a} />)}
          </div>
        </div>
      )}
    </div>
  )
}

export default AuctionDetailPage
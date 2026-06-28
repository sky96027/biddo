import { useState } from 'react'
import { placeBid, buyNow, setAutoBid, cancelAutoBid } from '../../api/bid'

interface Props {
  auctionId: number
  currentPrice: number
  minimumBidAmount: number
  buyNowPrice: number | null
  status: string
  isMySelling: boolean
  isHighestBidder: boolean
  onBidSuccess: () => void
}

const formatPrice = (p: number) => p.toLocaleString('ko-KR') + '원'

const BidForm = ({ auctionId, currentPrice, minimumBidAmount, buyNowPrice, status, isMySelling, isHighestBidder, onBidSuccess }: Props) => {
  const [tab, setTab] = useState<'manual' | 'auto'>('manual')
  const [bidAmount, setBidAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const isActive = status === 'ACTIVE'
  const disabled = !isActive || isMySelling || loading

  const handleInstantBid = async () => {
    setError(null)
    setLoading(true)
    try {
      await placeBid(auctionId, minimumBidAmount)
      setSuccess('입찰 완료!')
      onBidSuccess()
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '입찰에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleBid = async () => {
    const amount = Number(bidAmount.replace(/,/g, ''))
    if (!amount) return setError('입찰 금액을 입력하세요.')
    setError(null)
    setLoading(true)
    try {
      await placeBid(auctionId, amount)
      setSuccess('입찰 완료!')
      setBidAmount('')
      onBidSuccess()
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '입찰에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleBuyNow = async () => {
    setError(null)
    setLoading(true)
    try {
      await buyNow(auctionId)
      setSuccess('즉시 구매 완료!')
      onBidSuccess()
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '즉시 구매에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleAutoBid = async () => {
    const amount = Number(maxAmount.replace(/,/g, ''))
    if (!amount) return setError('최대 금액을 입력하세요.')
    setError(null)
    setLoading(true)
    try {
      await setAutoBid(auctionId, amount)
      setSuccess('자동 입찰 설정 완료!')
      setMaxAmount('')
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '자동 입찰 설정에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleCancelAutoBid = async () => {
    setError(null)
    setLoading(true)
    try {
      await cancelAutoBid(auctionId)
      setSuccess('자동 입찰 취소됨.')
    } catch {
      setError('자동 입찰 취소에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  if (isMySelling) return <p className="text-sm text-gray-400 p-4 bg-gray-50 rounded-lg text-center">내가 등록한 경매입니다.</p>
  if (!isActive) return <p className="text-sm text-gray-400 p-4 bg-gray-50 rounded-lg text-center">경매가 진행 중이 아닙니다.</p>
  if (isHighestBidder) return <p className="text-sm text-blue-600 p-4 bg-blue-50 rounded-lg text-center">현재 최상위 입찰자입니다.</p>

  return (
    <div className="border border-gray-200 rounded-lg p-4 space-y-3">
      <p className="text-sm text-gray-500">현재가: <span className="font-bold text-gray-800">{formatPrice(currentPrice)}</span></p>

      {error && <p className="text-sm text-red-500 bg-red-50 p-2 rounded">{error}</p>}
      {success && <p className="text-sm text-green-600 bg-green-50 p-2 rounded">{success}</p>}

      <div className="flex gap-2">
        <button onClick={() => setTab('manual')} className={`flex-1 py-1.5 text-sm rounded ${tab === 'manual' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}>수동 입찰</button>
        <button onClick={() => setTab('auto')} className={`flex-1 py-1.5 text-sm rounded ${tab === 'auto' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}>자동 입찰</button>
      </div>

      {tab === 'manual' ? (
        <div className="space-y-2">
          <div className="flex gap-2">
            <input
              type="text"
              value={bidAmount}
              onChange={(e) => setBidAmount(e.target.value)}
              placeholder="입찰 금액 (원)"
              disabled={disabled}
              className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            <button onClick={handleBid} disabled={disabled} className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50 whitespace-nowrap">
              {loading ? '처리 중...' : '입찰하기'}
            </button>
          </div>
          <button onClick={handleInstantBid} disabled={disabled} className="w-full py-2 bg-green-600 text-white text-sm rounded hover:bg-green-700 disabled:opacity-50">
            {loading ? '처리 중...' : `즉시 입찰 ${formatPrice(minimumBidAmount)}`}
          </button>
          {buyNowPrice && (
            <button onClick={handleBuyNow} disabled={disabled} className="w-full py-2 bg-orange-500 text-white text-sm rounded hover:bg-orange-600 disabled:opacity-50">
              즉시 구매 {formatPrice(buyNowPrice)}
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          <input
            type="text"
            value={maxAmount}
            onChange={(e) => setMaxAmount(e.target.value)}
            placeholder="최대 입찰 금액 (원)"
            disabled={disabled}
            className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
          <button onClick={handleAutoBid} disabled={disabled} className="w-full py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50">
            {loading ? '처리 중...' : '자동 입찰 설정'}
          </button>
          <button onClick={handleCancelAutoBid} disabled={disabled} className="w-full py-2 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300 disabled:opacity-50">
            자동 입찰 취소
          </button>
        </div>
      )}
    </div>
  )
}

export default BidForm
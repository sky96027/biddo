import type { BidHistoryItem } from '../../types'

const formatPrice = (p: number) => p.toLocaleString('ko-KR') + '원'

const bidTypeLabel: Record<string, string> = {
  MANUAL: '수동',
  AUTO: '자동',
  BUY_NOW: '즉시구매',
}

const BidHistory = ({ bids }: { bids: BidHistoryItem[] }) => {
  if (bids.length === 0) return <p className="text-sm text-gray-400 py-4 text-center">입찰 내역이 없습니다.</p>

  return (
    <div className="divide-y divide-gray-100">
      {bids.map((bid) => (
        <div key={bid.bidId} className="flex items-center justify-between py-2.5 text-sm">
          <div className="flex items-center gap-2">
            <span className="text-gray-700 font-medium">{bid.bidderNickname}</span>
            <span className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-500 rounded">
              {bidTypeLabel[bid.bidType]}
            </span>
          </div>
          <div className="text-right">
            <p className="font-bold text-blue-600">{formatPrice(bid.bidAmount)}</p>
            <p className="text-xs text-gray-400">{new Date(bid.createdAt).toLocaleString('ko-KR')}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

export default BidHistory
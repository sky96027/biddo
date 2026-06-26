import { Link } from 'react-router-dom'
import type { AuctionSummary } from '../../types'

const formatPrice = (p: number) => p.toLocaleString('ko-KR') + '원'

const statusLabel: Record<string, string> = {
  PENDING: '대기',
  ACTIVE: '진행중',
  ENDED: '종료',
  SOLD: '낙찰',
  CANCELLED: '취소',
}
const statusColor: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  ENDED: 'bg-gray-100 text-gray-600',
  SOLD: 'bg-blue-100 text-blue-700',
  PENDING: 'bg-yellow-100 text-yellow-700',
  CANCELLED: 'bg-red-100 text-red-600',
}

const AuctionCard = ({ auction }: { auction: AuctionSummary }) => (
  <Link to={`/auctions/${auction.auctionId}`} className="block border border-gray-200 rounded-lg overflow-hidden hover:shadow-md transition-shadow">
    <div className="w-full h-40 bg-gray-100 flex items-center justify-center">
      {auction.thumbnailUrl ? (
        <img src={auction.thumbnailUrl} alt={auction.title} className="w-full h-full object-cover" />
      ) : (
        <span className="text-gray-400 text-3xl">📦</span>
      )}
    </div>
    <div className="p-3">
      <div className="flex items-center justify-between mb-1">
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${statusColor[auction.status] ?? ''}`}>
          {statusLabel[auction.status]}
        </span>
        <span className="text-xs text-gray-400">{auction.categoryName}</span>
      </div>
      <p className="text-sm font-medium text-gray-800 truncate">{auction.title}</p>
      <p className="text-base font-bold text-blue-600 mt-1">{formatPrice(auction.currentPrice)}</p>
      <div className="flex items-center justify-between mt-1 text-xs text-gray-400">
        <span>입찰 {auction.bidCount}회</span>
        <span>{new Date(auction.endTime).toLocaleDateString('ko-KR')} 마감</span>
      </div>
    </div>
  </Link>
)

export default AuctionCard
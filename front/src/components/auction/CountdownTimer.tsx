import { useState, useEffect } from 'react'

interface Props {
  endTime: string
}

const CountdownTimer = ({ endTime }: Props) => {
  const [remaining, setRemaining] = useState(0)

  useEffect(() => {
    const calc = () => Math.max(0, Math.floor((new Date(endTime).getTime() - Date.now()) / 1000))
    setRemaining(calc())
    const id = setInterval(() => setRemaining(calc()), 1000)
    return () => clearInterval(id)
  }, [endTime])

  const h = Math.floor(remaining / 3600)
  const m = Math.floor((remaining % 3600) / 60)
  const s = remaining % 60

  const color = remaining < 600 ? 'text-red-600' : remaining < 3600 ? 'text-orange-500' : 'text-gray-700'

  if (remaining === 0) return <span className="text-gray-400 font-medium">경매 종료</span>

  return (
    <span className={`font-mono font-bold text-lg ${color}`}>
      {h > 0 && `${h}시간 `}{m}분 {String(s).padStart(2, '0')}초
    </span>
  )
}

export default CountdownTimer
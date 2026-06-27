import { useState, useEffect, FormEvent, ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { createAuction, getCategories } from '../api/auction'
import { uploadImage } from '../api/upload'
import type { Category, ItemCondition } from '../types'

const todayLocal = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

const MINUTES = ['00', '10', '20', '30', '40', '50']
const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))

interface DateTimePickerProps {
  value: string
  onChange: (value: string) => void
  required?: boolean
}

const DateTimePicker = ({ value, onChange, required }: DateTimePickerProps) => {
  const [date, setDate] = useState(value.slice(0, 10))
  const [hour, setHour] = useState(value.slice(11, 13) || '00')
  const [minute, setMinute] = useState(value.slice(14, 16) || '00')

  const emit = (d: string, h: string, m: string) => {
    onChange(d ? `${d}T${h}:${m}` : '')
  }

  return (
    <div className="flex gap-1">
      <input
        type="date"
        value={date}
        min={todayLocal()}
        required={required}
        onChange={(e) => { setDate(e.target.value); emit(e.target.value, hour, minute) }}
        className="input flex-1"
      />
      <select
        value={hour}
        onChange={(e) => { setHour(e.target.value); emit(date, e.target.value, minute) }}
        className="input w-16"
      >
        {HOURS.map((h) => <option key={h} value={h}>{h}</option>)}
      </select>
      <span className="self-center text-gray-500">:</span>
      <select
        value={minute}
        onChange={(e) => { setMinute(e.target.value); emit(date, hour, e.target.value) }}
        className="input w-16"
      >
        {MINUTES.map((m) => <option key={m} value={m}>{m}</option>)}
      </select>
    </div>
  )
}

const StartTimeField = ({ value, onChange }: { value: string; onChange: (v: string) => void }) => {
  const [scheduled, setScheduled] = useState(false)

  const toggle = () => {
    if (scheduled) onChange('')
    setScheduled((prev) => !prev)
  }

  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={toggle}
        className={`w-full flex items-center justify-between px-3 py-2 rounded border text-sm transition-colors ${
          scheduled
            ? 'border-blue-500 bg-blue-50 text-blue-700'
            : 'border-gray-300 bg-gray-50 text-gray-500 hover:bg-gray-100'
        }`}
      >
        <span>{scheduled ? '예약 시작' : '즉시 시작'}</span>
        <span className={`w-9 h-5 flex items-center rounded-full transition-colors ${scheduled ? 'bg-blue-500' : 'bg-gray-300'}`}>
          <span className={`w-4 h-4 bg-white rounded-full shadow transform transition-transform ${scheduled ? 'translate-x-4' : 'translate-x-0.5'}`} />
        </span>
      </button>
      {scheduled && (
        <DateTimePicker value={value} onChange={onChange} />
      )}
    </div>
  )
}

const conditionOptions: { value: ItemCondition; label: string }[] = [
  { value: 'NEW', label: '새 상품' },
  { value: 'LIKE_NEW', label: '거의 새 것' },
  { value: 'GOOD', label: '상태 좋음' },
  { value: 'FAIR', label: '보통' },
  { value: 'POOR', label: '상태 나쁨' },
]

const AuctionCreatePage = () => {
  const navigate = useNavigate()
  const [categories, setCategories] = useState<Category[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [uploadingImages, setUploadingImages] = useState(false)
  const [imageUrls, setImageUrls] = useState<string[]>([])

  const [form, setForm] = useState({
    title: '',
    description: '',
    categoryId: '',
    condition: 'GOOD' as ItemCondition,
    startingPrice: '',
    buyNowPrice: '',
    startTime: '',
    endTime: '',
  })

  useEffect(() => {
    getCategories().then(setCategories).catch(() => {})
  }, [])

  const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  const handleImageChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    if (files.length === 0) return
    setUploadingImages(true)
    setError(null)
    try {
      const urls = await Promise.all(files.map((f) => uploadImage(f)))
      setImageUrls((prev) => [...prev, ...urls])
    } catch {
      setError('이미지 업로드에 실패했습니다.')
    } finally {
      setUploadingImages(false)
    }
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const { auctionId } = await createAuction({
        title: form.title,
        description: form.description,
        categoryId: Number(form.categoryId),
        condition: form.condition,
        startingPrice: Number(form.startingPrice.replace(/,/g, '')),
        buyNowPrice: form.buyNowPrice ? Number(form.buyNowPrice.replace(/,/g, '')) : undefined,
        startTime: form.startTime ? new Date(form.startTime).toISOString() : undefined,
        endTime: new Date(form.endTime).toISOString(),
        imageUrls,
      })
      navigate(`/auctions/${auctionId}`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '경매 등록에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">경매 등록</h1>
      {error && <p className="text-sm text-red-500 bg-red-50 p-3 rounded mb-4">{error}</p>}

      <form onSubmit={handleSubmit} className="space-y-5">
        <Field label="제목">
          <input name="title" value={form.title} onChange={handleChange} required
            className="input" placeholder="상품명을 입력하세요" />
        </Field>

        <Field label="설명">
          <textarea name="description" value={form.description} onChange={handleChange} required rows={4}
            className="input resize-none" placeholder="상품 상태, 구성품 등을 자세히 적어주세요" />
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="카테고리">
            <select name="categoryId" value={form.categoryId} onChange={handleChange} required className="input">
              <option value="">선택</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="상품 상태">
            <select name="condition" value={form.condition} onChange={handleChange} className="input">
              {conditionOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Field label="시작가 (원)">
            <input name="startingPrice" value={form.startingPrice} onChange={handleChange} required
              className="input" placeholder="10000" />
          </Field>
          <Field label="즉시 구매가 (원, 선택)">
            <input name="buyNowPrice" value={form.buyNowPrice} onChange={handleChange}
              className="input" placeholder="선택 사항" />
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Field label="경매 시작 시간">
            <StartTimeField
              value={form.startTime}
              onChange={(v) => setForm((p) => ({ ...p, startTime: v }))}
            />
          </Field>
          <Field label="경매 종료 시간">
            <DateTimePicker value={form.endTime} onChange={(v) => setForm((p) => ({ ...p, endTime: v }))} required />
          </Field>
        </div>

        <Field label="이미지">
          <input type="file" accept="image/*" multiple onChange={handleImageChange} disabled={uploadingImages}
            className="text-sm text-gray-600" />
          {uploadingImages && <p className="text-xs text-gray-400 mt-1">업로드 중...</p>}
          {imageUrls.length > 0 && (
            <div className="flex gap-2 mt-2 flex-wrap">
              {imageUrls.map((url, i) => (
                <div key={i} className="relative">
                  <img src={url} alt="" className="w-16 h-16 object-cover rounded border" />
                  <button type="button" onClick={() => setImageUrls((prev) => prev.filter((_, j) => j !== i))}
                    className="absolute -top-1 -right-1 bg-red-500 text-white rounded-full w-4 h-4 text-xs flex items-center justify-center">×</button>
                </div>
              ))}
            </div>
          )}
        </Field>

        <button type="submit" disabled={loading || uploadingImages}
          className="w-full py-2.5 bg-blue-600 text-white font-medium rounded hover:bg-blue-700 disabled:opacity-50">
          {loading ? '등록 중...' : '경매 등록'}
        </button>
      </form>
    </div>
  )
}

const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div>
    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
    {children}
  </div>
)

export default AuctionCreatePage
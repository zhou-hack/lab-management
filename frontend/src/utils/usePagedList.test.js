import { describe, it, expect, vi } from 'vitest'
import { usePagedList } from './usePagedList'

function pageFetcher(pages) {
  // pages: { [pageNum]: { records: [...], total: n } }
  return vi.fn(async (params) => pages[params.pageNum] || { records: [], total: 0 })
}

describe('usePagedList 分页逻辑', () => {
  it('加载后填充 rows/total', async () => {
    const fetcher = vi.fn(async () => ({ records: [{ id: '1' }, { id: '2' }], total: 31 }))
    const pager = usePagedList(fetcher)
    await pager.load()
    expect(pager.rows).toHaveLength(2)
    expect(pager.total).toBe(31)
    expect(pager.loading).toBe(false)
  })

  it('默认页大小为 20，且加载参数携带页码与页大小', async () => {
    const fetcher = vi.fn(async () => ({ records: [], total: 100 }))
    const pager = usePagedList(fetcher)
    await pager.onPageChange(3)
    expect(fetcher).toHaveBeenCalledWith({ pageNum: 3, pageSize: 20 })
    expect(pager.pageNum).toBe(3)
    expect(pager.pageSize).toBe(20)
  })

  it('search 重置到第 1 页后加载', async () => {
    const fetcher = vi.fn(async () => ({ records: [], total: 100 }))
    const pager = usePagedList(fetcher)
    await pager.onPageChange(4)
    fetcher.mockClear()
    await pager.search()
    expect(pager.pageNum).toBe(1)
    expect(fetcher).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
  })

  it('onSizeChange 更新页大小并强制回第 1 页', async () => {
    const fetcher = vi.fn(async () => ({ records: [], total: 100 }))
    const pager = usePagedList(fetcher)
    await pager.onPageChange(5)
    fetcher.mockClear()
    await pager.onSizeChange(50)
    expect(pager.pageSize).toBe(50)
    expect(pager.pageNum).toBe(1)
    expect(fetcher).toHaveBeenCalledWith({ pageNum: 1, pageSize: 50 })
  })

  it('末页数据被删空时自动回退到新的末页', async () => {
    // 第 2 页原有数据，删除唯一行后 total=1 只剩 1 页
    const fetcher = pageFetcher({
      2: { records: [], total: 1 },
      1: { records: [{ id: '9' }], total: 1 }
    })
    const pager = usePagedList(fetcher)
    pager.pageNum = 2
    await pager.load()
    expect(pager.pageNum).toBe(1)
    expect(pager.rows).toEqual([{ id: '9' }])
  })

  it('请求失败保留现有数据且 loading 归位', async () => {
    const fetcher = vi.fn()
    fetcher.mockResolvedValueOnce({ records: [{ id: '1' }], total: 1 })
    fetcher.mockRejectedValueOnce(new Error('network'))
    const pager = usePagedList(fetcher)
    await pager.load()
    expect(pager.rows).toEqual([{ id: '1' }])
    await pager.load()
    expect(pager.rows).toEqual([{ id: '1' }])
    expect(pager.loading).toBe(false)
  })

  it('响应缺字段时 rows/total 回落为空数组与 0', async () => {
    const pager = usePagedList(async () => undefined)
    await pager.load()
    expect(pager.rows).toEqual([])
    expect(pager.total).toBe(0)
  })
})

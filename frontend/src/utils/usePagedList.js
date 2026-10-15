import { reactive, ref } from 'vue'

/**
 * 分页列表通用逻辑（物料工作台各面板共用）
 *
 * 封装 rows/total/pageNum/pageSize/loading 与加载调度：
 * - search()：查询条件变化，重置到第 1 页后加载
 * - onPageChange(page)：页码切换
 * - onSizeChange(size)：改页大小时先更新 pageSize 再强制回第 1 页（修复原地换页问题）
 * - 末页数据被删空时自动回退到新的末页，避免停留在空页
 *
 * 约定：fetcher(params) 返回 { records, total }；失败已由全局拦截器提示，
 * 内部捕获异常并保留现有数据。
 *
 * @param {(params: {pageNum: number, pageSize: number}) => Promise} fetcher
 * @param {number} defaultPageSize 默认 20，上限由后端校验（100）
 * @returns 响应式分页对象，模板中直接使用 pager.rows / pager.loading 等
 */
export function usePagedList(fetcher, defaultPageSize = 20) {
  const rows = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(defaultPageSize)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const data = await fetcher({ pageNum: pageNum.value, pageSize: pageSize.value })
      rows.value = data?.records ?? []
      total.value = data?.total ?? 0
      const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
      if (pageNum.value > maxPage) {
        pageNum.value = maxPage
        await load()
      }
    } catch {
      // 请求失败已由全局拦截器提示，保留现有数据
    } finally {
      loading.value = false
    }
  }

  function search() {
    pageNum.value = 1
    return load()
  }

  function onPageChange(page) {
    pageNum.value = page
    return load()
  }

  function onSizeChange(size) {
    pageSize.value = size
    pageNum.value = 1
    return load()
  }

  return reactive({ rows, total, pageNum, pageSize, loading, load, search, onPageChange, onSizeChange })
}

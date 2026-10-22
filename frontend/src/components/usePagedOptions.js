import { computed, nextTick, onBeforeUnmount, ref, useId } from 'vue'

/**
 * Typeahead 分页远程选项（团队统一规范）：
 * 默认 15 条/页从数据库请求；输入关键词走后端索引搜索（同样分页）；
 * 下拉滚动到底自动加载下一页，直至关键词命中的数据全部拉完，不一次性全量渲染。
 *
 * fetchPage({ pageNum, pageSize, keyword }) 须返回 { records, total }；
 * 返回项直接驱动 el-select：:remote-method="searchOptions"、:loading="searching"、
 * :popper-class="popperClass"、@visible-change="onVisibleChange"，
 * 模板尾部用 loadingMore / noMore 渲染加载状态行。
 */
export function usePagedOptions(fetchPage, { pageSize = 15 } = {}) {
  const options = ref([])
  const searching = ref(false)
  const loadingMore = ref(false)
  const keyword = ref('')
  const pageNum = ref(0)
  const total = ref(0)
  // 保留基础样式类 item-select-popper，追加实例唯一类（useId 组件实例级，HMR/多实例不撞号）
  const instanceId = useId().replace(/[^\w-]/g, '')
  const popperClass = `item-select-popper item-select-popper-${instanceId}`

  const noMore = computed(() => pageNum.value > 0 && pageNum.value * pageSize >= total.value)

  async function searchOptions(kw) {
    keyword.value = kw || ''
    searching.value = true
    try {
      const data = await fetchPage({ pageNum: 1, pageSize, keyword: keyword.value || undefined })
      options.value = data.records
      total.value = data.total
      pageNum.value = 1
    } catch {
      options.value = []
      total.value = 0
      pageNum.value = 0
    } finally {
      searching.value = false
    }
  }

  async function loadMore() {
    if (searching.value || loadingMore.value || noMore.value) return
    loadingMore.value = true
    try {
      const data = await fetchPage({
        pageNum: pageNum.value + 1,
        pageSize,
        keyword: keyword.value || undefined
      })
      options.value = [...options.value, ...data.records]
      total.value = data.total
      pageNum.value += 1
    } catch {
      // 拉取失败保持现状，再次滚动到底自动重试
    } finally {
      loadingMore.value = false
    }
  }

  function onPopperScroll(e) {
    const el = e.target
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 8) loadMore()
  }

  /** 下拉展开时挂载滚动监听（popper 首次展开才渲染 DOM；__pagedScrollBound 防重复绑定） */
  async function onVisibleChange(visible) {
    if (!visible) return
    await nextTick()
    const wrap = document.querySelector(`.item-select-popper-${instanceId} .el-select-dropdown__wrap`)
    if (wrap && !wrap.__pagedScrollBound) {
      wrap.addEventListener('scroll', onPopperScroll)
      wrap.__pagedScrollBound = true
    }
  }

  onBeforeUnmount(() => {
    const wrap = document.querySelector(`.item-select-popper-${instanceId} .el-select-dropdown__wrap`)
    if (wrap) wrap.removeEventListener('scroll', onPopperScroll)
  })

  return { options, searching, loadingMore, noMore, popperClass, searchOptions, onVisibleChange }
}

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  pageMyBookings: vi.fn()
}))

import { pageMyBookings } from '@/api/equipment'
import RejectedBookings from './RejectedBookings.vue'

const rows = [
  {
    id: '1',
    documentNo: 'EQ20261020001',
    equipmentName: '路由器',
    assetCode: 'ZC-001',
    modelCode: 'AR6120',
    purpose: '课程实验',
    approvalTeacherName: '王老师',
    status: 'REJECTED',
    rejectReason: '用途与审批要求不符',
    rejectedAt: '2026-10-20T08:00:00.000Z',
    startAt: '2026-10-21T01:00:00.000Z',
    endAt: '2026-10-21T03:00:00.000Z',
    createTime: '2026-10-20T07:00:00.000Z'
  }
]

const mountPage = () =>
  mount(RejectedBookings, {
    global: { plugins: [ElementPlus] }
  })

describe('查看驳回申请页', () => {
  beforeEach(() => {
    pageMyBookings.mockResolvedValue({ records: rows, total: rows.length })
  })

  it('挂载即按 REJECTED 查询并渲染驳回列表（含驳回原因列）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(pageMyBookings).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'REJECTED' })
    )
    expect(wrapper.find('.el-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('EQ20261020001')
    expect(wrapper.text()).toContain('用途与审批要求不符')
    wrapper.unmount()
  })

  it('点击「详情」打开弹窗展示驳回原因与申请信息', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const detailBtn = wrapper.findAll('button').find((b) => b.text().includes('详情'))
    expect(detailBtn).toBeDefined()
    await detailBtn.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-dialog').text()).toContain('申请详情')
    expect(wrapper.text()).toContain('用途与审批要求不符')
    wrapper.unmount()
  })
})

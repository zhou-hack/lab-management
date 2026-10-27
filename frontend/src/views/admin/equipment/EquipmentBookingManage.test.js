import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  adminReview: vi.fn(),
  checkoutBooking: vi.fn(),
  pageAllBookings: vi.fn(),
  returnBooking: vi.fn()
}))

import { pageAllBookings } from '@/api/equipment'
import EquipmentBookingManage from './EquipmentBookingManage.vue'

const now = Date.now()
const rows = [
  {
    id: '1',
    documentNo: 'EQ1',
    applicantName: '张同学',
    approvalTeacherName: '王老师',
    equipmentName: '路由器',
    assetCode: 'R01',
    purpose: '测试',
    startAt: new Date(now + 3600e3).toISOString(),
    endAt: new Date(now + 7200e3).toISOString(),
    status: 'PENDING_TEACHER',
    createTime: new Date(now - 3600e3).toISOString()
  },
  {
    id: '2',
    documentNo: 'EQ2',
    applicantName: '张同学',
    approvalTeacherName: '王老师',
    equipmentName: '交换机',
    assetCode: 'SW01',
    purpose: '测试',
    startAt: new Date(now + 3600e3).toISOString(),
    endAt: new Date(now + 7200e3).toISOString(),
    status: 'PENDING_ADMIN',
    createTime: new Date(now - 3600e3).toISOString()
  },
  {
    id: '3',
    documentNo: 'EQ3',
    applicantName: '王老师',
    approvalTeacherName: '—',
    equipmentName: '服务器',
    assetCode: 'SRV01',
    purpose: '测试',
    startAt: new Date(now + 3600e3).toISOString(),
    endAt: new Date(now + 7200e3).toISOString(),
    status: 'APPROVED',
    createTime: new Date(now - 3600e3).toISOString()
  },
  {
    id: '4',
    documentNo: 'EQ4',
    applicantName: '张同学',
    approvalTeacherName: '王老师',
    equipmentName: '打印机',
    assetCode: 'PRN01',
    purpose: '测试',
    startAt: new Date(now - 2 * 86400e3).toISOString(),
    endAt: new Date(now - 86400e3).toISOString(),
    status: 'OVERDUE',
    createTime: new Date(now - 7 * 86400e3).toISOString()
  }
]

const mountPage = () =>
  mount(EquipmentBookingManage, {
    global: {
      plugins: [ElementPlus],
      // v-permission 指令在真实入口注册，测试中以空指令桩替代
      directives: { permission: {} }
    }
  })

describe('管理员设备使用审批页操作列', () => {
  beforeEach(() => {
    pageAllBookings.mockResolvedValue({ records: rows, total: rows.length })
  })

  it('待教师初审单操作栏不显示通过/驳回；PENDING_ADMIN 显示终审按钮；已通过显示借出登记；OVERDUE 显示归还登记与逾期标记', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const trs = wrapper.findAll('.el-table__row')
    expect(trs.length).toBe(4)
    // 待教师初审：操作栏无任何按钮，仅占位
    expect(trs[0].findAll('button').length).toBe(0)
    expect(trs[0].text()).not.toContain('通过')
    expect(trs[0].text()).not.toContain('驳回')
    expect(trs[0].text()).toContain('—')
    // PENDING_ADMIN：通过/驳回两个按钮且均可用
    const adminButtons = trs[1].findAll('button')
    expect(adminButtons.length).toBe(2)
    for (const button of adminButtons) {
      expect(button.attributes('disabled')).toBeUndefined()
    }
    expect(trs[1].text()).toContain('通过')
    expect(trs[1].text()).toContain('驳回')
    // 已通过：显示借出登记
    expect(trs[2].text()).toContain('借出登记')
    // OVERDUE：显示归还登记 + 逾期未还标记；不显示通过/驳回/借出登记
    const overdueButtons = trs[3].findAll('button')
    expect(overdueButtons.length).toBe(1)
    expect(overdueButtons[0].text()).toContain('归还登记')
    expect(trs[3].text()).not.toContain('通过')
    expect(trs[3].text()).not.toContain('借出登记')
    expect(trs[3].text()).toContain('逾期未还')
    wrapper.unmount()
  })
})

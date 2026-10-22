import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 区块组件以桩替代，避免真实渲染触发 API 请求
vi.mock('@/views/admin/equipment/AssetManage.vue', () => ({
  default: defineComponent({
    name: 'AssetSection',
    props: { active: { type: Boolean, default: false } },
    emits: ['count'],
    template: '<div class="asset_stub">设备台账区</div>'
  })
}))

import AssetPage from './AssetPage.vue'

const mountPage = () =>
  mount(AssetPage, {
    global: { plugins: [ElementPlus] }
  })

describe('设备台账独立页', () => {
  it('渲染页头「设备台账」并承载台账区块（active=true 挂载即加载）', () => {
    const wrapper = mountPage()
    expect(wrapper.find('.t-title').text()).toContain('设备台账')
    const section = wrapper.findComponent({ name: 'AssetSection' })
    expect(section.exists()).toBe(true)
    expect(section.props('active')).toBe(true)
    expect(wrapper.find('.asset_stub').exists()).toBe(true)
    wrapper.unmount()
  })
})

import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 区块组件以桩替代，避免真实渲染触发 API 请求
vi.mock('@/views/admin/equipment/ModelManage.vue', () => ({
  default: defineComponent({
    name: 'ModelSection',
    props: { active: { type: Boolean, default: false } },
    emits: ['count'],
    template: '<div class="model_stub">型号区</div>'
  })
}))

import ModelPage from './ModelPage.vue'

const mountPage = () =>
  mount(ModelPage, {
    global: { plugins: [ElementPlus] }
  })

describe('设备库存独立页', () => {
  it('渲染页头「设备库存」并承载型号区块（active=true 挂载即加载）', () => {
    const wrapper = mountPage()
    expect(wrapper.find('.t-title').text()).toContain('设备库存')
    const section = wrapper.findComponent({ name: 'ModelSection' })
    expect(section.exists()).toBe(true)
    expect(section.props('active')).toBe(true)
    expect(wrapper.find('.model_stub').exists()).toBe(true)
    wrapper.unmount()
  })
})

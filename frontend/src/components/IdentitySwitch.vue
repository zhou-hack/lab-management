<template>
  <el-dropdown
    trigger="click"
    @command="onSwitch"
  >
    <span class="identity_switch">
      <el-icon class="identity_switch__icon"><Switch /></el-icon>
      <span class="identity_switch__label">{{ store.identityLabel }}</span>
      <el-icon class="identity_switch__arrow"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="identity in store.identities"
          :key="identity.code"
          :command="identity.code"
          :disabled="identity.code === store.currentIdentity"
        >
          <span class="identity_switch__item">{{ identity.label }}</span>
          <span class="identity_switch__desc">{{ identity.description }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Switch } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const router = useRouter()

async function onSwitch(roleCode) {
  try {
    await store.switchIdentity(roleCode)
    ElMessage.success(`已切换为「${store.identityLabel}」身份`)
    await router.push(store.homePath)
  } catch {
    // 403（无该身份）/网络错误由 Axios 拦截器统一提示
  }
}
</script>

<style scoped>
.identity_switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 13px;
  font-weight: var(--fw-medium, 500);
  color: var(--g2);
  cursor: pointer;
  background: var(--brand-a08);
  border-radius: var(--r-full);
  white-space: nowrap;
}

.identity_switch:hover {
  color: var(--brand, #3f86ff);
  background: var(--brand-a12);
}

.identity_switch__icon {
  color: var(--brand, #3f86ff);
}

.identity_switch__arrow {
  font-size: 12px;
}

.identity_switch__item {
  font-weight: var(--fw-medium, 500);
}

.identity_switch__desc {
  margin-left: 8px;
  font-size: 12px;
  color: var(--g3, #6b7280);
}
</style>
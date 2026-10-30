<template>
  <div class="login_page">
    <div class="login_card sweep">
      <div class="login_card__brand">
        <span class="login_card__mark">
          <el-icon><Tools /></el-icon>
        </span>
      </div>
      <h1 class="login_card__title">
        实验室设备管理系统
      </h1>
      <p class="login_card__sub t-sub">
        学生 / 老师 / 管理员 统一入口
      </p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item
          label="账号"
          prop="accountName"
        >
          <el-input
            v-model="form.accountName"
            placeholder="请输入账号 / 学工号"
            size="large"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item
          label="密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="submit"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-button
          class="login_submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
        >
          登 录
        </el-button>
      </el-form>

      <!-- 开发调试快捷登录：import.meta.env.DEV 条件渲染，生产构建整体剔除 -->
      <div
        v-if="isDev"
        class="login_quick"
      >
        <el-divider class="login_quick__divider">
          开发快捷登录
        </el-divider>
        <div class="login_quick__row">
          <el-button
            v-for="acc in quickAccounts"
            :key="acc.account"
            size="small"
            :disabled="loading"
            @click="quickLogin(acc)"
          >
            {{ acc.label }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Tools, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const formRef = ref()
const form = reactive({ accountName: '', password: '' })

const rules = {
  accountName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    // 校验未通过：Element 已在表单项上提示，直接返回
    return
  }
  loading.value = true
  try {
    await store.login({ accountName: form.accountName, password: form.password })
    router.push(route.query.redirect || '/')
  } catch {
    // 登录失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ---------------- 开发快捷登录（仅 DEV，生产构建剔除） ---------------- */

const isDev = import.meta.env.DEV
const quickAccounts = import.meta.env.DEV
  ? [
      { label: '管理员', account: 'admin', password: 'Admin123456' },
      { label: '运维管理员', account: 'opsadmin', password: 'Admin123456' },
      { label: '老师', account: 'T20240101', password: 'Admin123456' },
      { label: '学生', account: 'S20240101', password: 'Admin123456' },
      // 种子库仅 1 个学生账号，其持有生效中的助理授权，登录即含 ASSISTANT 角色
      { label: '学生代理', account: 'S20240101', password: 'Admin123456' }
    ]
  : []

async function quickLogin(acc) {
  loading.value = true
  try {
    await store.login({ accountName: acc.account, password: acc.password })
    router.push(route.query.redirect || '/')
  } catch {
    // 登录失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 页面底保持极浅灰纯色（规范第 3 节：禁止给页面背景用渐变） */
.login_page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
  background: var(--bg);
}

/* 登录卡：特大容器 r-xl 22px（规范第 5 节） */
.login_card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  padding: 34px 32px 30px;
  background: var(--surface);
  border-radius: var(--r-xl);
  border: .5px solid var(--hair);
  box-shadow: 0 24px 64px -28px rgba(15, 30, 60, .18);
}

.login_card__brand {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.login_card__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 24px;
  box-shadow: var(--brand-shadow-lg);
}

.login_card__title {
  margin: 0;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  text-align: center;
}

.login_card__sub {
  margin: 6px 0 22px;
  text-align: center;
}

/* 输入框高 46px（规范 7.6） */
.login_card :deep(.el-input__wrapper) {
  min-height: 46px;
}
.login_card :deep(.el-input__prefix) {
  color: var(--g4);
}
.login_card :deep(.el-input__wrapper.is-focus .el-input__prefix),
.login_card :deep(.el-input__wrapper:focus-within .el-input__prefix) {
  color: var(--brand);
}

.login_submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border-radius: var(--r-full);
  font-size: 15px;
  letter-spacing: 4px;
}

.login_submit:active {
  transform: scale(.985);
}

/* 开发快捷登录：低调的调试区块，不参与正式视觉层级 */
.login_quick__divider {
  margin: 20px 0 12px;
}

.login_quick__divider :deep(.el-divider__text) {
  font-size: 12px;
  color: var(--g4);
}

.login_quick__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.login_quick__row .el-button {
  flex: 1 1 30%;
  margin: 0;
}
</style>

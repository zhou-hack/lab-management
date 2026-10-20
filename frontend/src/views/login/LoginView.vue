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

      <!-- 分段控件：账号密码 / 手机验证码 -->
      <div class="login_seg">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="login_seg__item"
          :class="{ 'login_seg__item--on': mode === tab.value }"
          @click="mode = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <template v-if="mode === 'password'">
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
        </template>
        <template v-else>
          <el-form-item
            label="手机号"
            prop="phone"
          >
            <el-input
              v-model="form.phone"
              placeholder="11 位手机号"
              size="large"
              maxlength="11"
            >
              <template #prefix>
                <el-icon><Iphone /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item
            label="验证码"
            prop="code"
          >
            <div class="login_code_row">
              <el-input
                v-model="form.code"
                placeholder="6 位验证码"
                size="large"
                maxlength="6"
              />
              <el-button
                class="login_code_btn"
                size="large"
                :disabled="countdown > 0"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
        </template>

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

      <!-- 开发环境：测试账号一键登录（生产构建不含此区块） -->
      <div
        v-if="isDev"
        class="login_quick"
      >
        <span class="login_quick__label">测试账号</span>
        <button
          type="button"
          class="login_quick__chip"
          :disabled="loading"
          @click="quickLoginAdmin"
        >
          管理员 admin
        </button>
        <button
          type="button"
          class="login_quick__chip"
          :disabled="loading"
          @click="quickLoginPhone('13800000001')"
        >
          王老师（老师）
        </button>
        <button
          type="button"
          class="login_quick__chip"
          :disabled="loading"
          @click="quickLoginPhone('13800000002')"
        >
          张同学（学生）
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Iphone, Lock, Tools, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const tabs = [
  { label: '账号密码', value: 'password' },
  { label: '手机验证码', value: 'phone' }
]

const mode = ref('password')
const loading = ref(false)
const formRef = ref()
const form = reactive({ accountName: '', password: '', phone: '', code: '' })

/** 开发环境标识：测试账号一键登录区块仅在 dev 构建出现 */
const isDev = import.meta.env.DEV

/** 骨架环境模拟账号（无认证后端，登录由 store.loginAsTest 本地完成） */
const MOCK_ADMIN = { accountName: 'admin', password: 'Admin123' }
const MOCK_PHONES = {
  13800000001: 'TEACHER',
  13800000002: 'STUDENT'
}

const rules = {
  accountName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

// 60s 重发倒计时
const countdown = ref(0)
let timer = null

/** 模拟短信通道：不发请求，直接回填固定验证码 */
function sendCode() {
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  if (!MOCK_PHONES[form.phone]) {
    ElMessage.warning('骨架环境仅支持测试手机号 13800000001 / 13800000002')
    return
  }
  form.code = '123456'
  ElMessage.success('模拟通道：验证码已自动回填')
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
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
    if (mode.value === 'password') {
      if (form.accountName === MOCK_ADMIN.accountName && form.password === MOCK_ADMIN.password) {
        store.loginAsTest('ADMIN')
      } else {
        ElMessage.error('账号或密码错误（骨架环境：admin / Admin123）')
        return
      }
    } else {
      const role = MOCK_PHONES[form.phone]
      if (!role) {
        ElMessage.error('骨架环境仅支持测试手机号 13800000001 / 13800000002')
        return
      }
      store.loginAsTest(role)
    }
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

/** 一键登录（仅 dev）：管理员账密填充后直接提交 */
async function quickLoginAdmin() {
  mode.value = 'password'
  form.accountName = MOCK_ADMIN.accountName
  form.password = MOCK_ADMIN.password
  await submit()
}

/** 一键登录（仅 dev）：填充手机号 → 模拟通道自动回填验证码 → 提交 */
async function quickLoginPhone(phone) {
  mode.value = 'phone'
  form.phone = phone
  form.code = ''
  sendCode()
  await submit()
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

/* 分段控件（规范 7.6）：sunken 槽 + 选中项白底浮起 + 蓝字，全圆角 */
.login_seg {
  display: flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 20px;
  background: var(--surface-sunken);
  border-radius: var(--r-full);
}

.login_seg__item {
  flex: 1;
  height: 34px;
  border: none;
  border-radius: var(--r-full);
  background: transparent;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g3);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.login_seg__item--on {
  background: var(--surface);
  color: var(--brand);
  box-shadow: 0 2px 8px -2px rgba(15, 30, 60, .12);
}

.login_code_row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.login_code_btn {
  flex-shrink: 0;
  border-radius: var(--r-full);
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

/* 测试账号一键登录（仅 dev）：细线分隔 + 全圆角小胶囊 */
.login_quick {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: .5px solid var(--hair);
}

.login_quick__label {
  font-size: 12px;
  color: var(--g3);
}

.login_quick__chip {
  height: 28px;
  padding: 0 12px;
  border: .5px solid var(--hair-brand);
  border-radius: var(--r-full);
  background: var(--brand-a04);
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.login_quick__chip:hover:not(:disabled) {
  background: var(--brand-a08);
  transform: translateY(-1px);
}

.login_quick__chip:active:not(:disabled) {
  transform: scale(.97);
}

.login_quick__chip:disabled {
  opacity: .5;
  cursor: not-allowed;
}
</style>

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

      <!-- dev 环境测试账号快捷填充（生产构建不渲染） -->
      <div
        v-if="isDev"
        class="login_test"
      >
        <span class="login_test__label">测试账号</span>
        <div class="login_test__btns">
          <button
            type="button"
            class="login_test__btn"
            @click="fillAccount('admin')"
          >
            管理员
          </button>
          <button
            type="button"
            class="login_test__btn"
            @click="fillAccount('opsadmin')"
          >
            运维
          </button>
          <button
            type="button"
            class="login_test__btn"
            @click="fillSms('13800000002')"
          >
            老师
          </button>
          <button
            type="button"
            class="login_test__btn"
            @click="fillSms('13800000001')"
          >
            学生
          </button>
        </div>
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
import { sendSmsCode } from '@/api/user'

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

async function sendCode() {
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  try {
    const res = await sendSmsCode(form.phone)
    // dev 环境模拟通道：验证码直接回显自动回填；其他环境 devCode 为空
    if (res && res.devCode) {
      form.code = res.devCode
      ElMessage.success('模拟通道：验证码已自动回填')
    } else {
      ElMessage.success('验证码已发送')
    }
    countdown.value = (res && res.ttlSeconds) || 60
  } catch {
    // 接口错误提示由请求拦截器统一处理；失败时不启动倒计时，允许立即重试
    return
  }
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
    await store.login({ mode: mode.value, ...form })
    // 强制改密（管理员密码到期 / 老师首登）优先于目标路由
    if (store.mustSetPassword) {
      router.push('/set_password')
      return
    }
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

// ---------- dev 测试账号快捷填充 ----------
const isDev = import.meta.env.DEV

function fillAccount(accountName) {
  mode.value = 'password'
  form.accountName = accountName
  form.password = 'Admin123456'
}

async function fillSms(phone) {
  mode.value = 'phone'
  form.phone = phone
  form.code = ''
  // dev 模拟通道：发送成功后验证码自动回填；60s 间隔内重发会提示 409，不启动倒计时
  await sendCode()
}
</script>

<style scoped>
.login_page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
  background:
    radial-gradient(900px 500px at 80% 10%, var(--brand-a12), transparent 60%),
    var(--bg);
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

/* dev 测试账号：低调弱化，贴卡片底部 */
.login_test {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 22px;
  padding-top: 16px;
  border-top: .5px dashed var(--hair);
}

.login_test__label {
  font-size: 12px;
  color: var(--g4);
  letter-spacing: 2px;
}

.login_test__btns {
  display: flex;
  gap: 10px;
}

.login_test__btn {
  height: 28px;
  padding: 0 16px;
  border: .5px solid var(--hair);
  border-radius: var(--r-full);
  background: var(--surface-sunken);
  font-size: 12px;
  color: var(--g3);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.login_test__btn:hover {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-a12);
}
</style>

<template>
  <div class="login_page">
    <div class="login_card">
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
              placeholder="学号 / 工号"
              size="large"
            />
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
            />
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
            />
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
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
    await sendSmsCode(form.phone)
    ElMessage.success('验证码已发送')
  } catch {
    // 接口错误提示由请求拦截器统一处理；后端未就绪时此处不再重复提示
  }
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
    await store.login({ mode: mode.value, ...form })
    // redirect 可能是未登录时被通配符污染的 /404，或历史书签里的非法地址：
    // 仅接受站内正常路径，其余回退根路径（由守卫按身份分流到实际首页）
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const safeRedirect = redirect && redirect !== '/404' && redirect.startsWith('/') ? redirect : '/'
    router.push(safeRedirect)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
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
  width: 100%;
  max-width: 400px;
  padding: 34px 32px 30px;
  background: var(--surface);
  border-radius: var(--r-xl);
  border: .5px solid var(--hair);
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
</style>

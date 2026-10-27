<template>
  <div class="login_page">
    <div class="login_card sweep">
      <div class="login_brand">
        <span class="login_brand__mark">
          <el-icon :size="26">
            <Monitor />
          </el-icon>
        </span>
        <h1 class="login_card__title">
          实验室设备管理系统
        </h1>
        <p class="login_card__sub">
          设备台账 · 使用申请 · 审批借还
        </p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item
          label="账号"
          prop="username"
        >
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
            size="large"
            :prefix-icon="User"
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
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
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

        <div class="login_fills">
          <span class="login_fills__label">演示账号快捷填充</span>
          <div class="login_fills__btns">
            <el-button
              v-for="account in DEMO_ACCOUNTS"
              :key="account.username"
              class="login_fills__btn"
              @click="fillAccount(account)"
            >
              {{ account.label }}
            </el-button>
          </div>
        </div>
      </el-form>
    </div>
    <p class="login_footer t-hint">
      开发 / 测试演示环境 · 快捷填充仅用于联调验证
    </p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Monitor, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
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
    await store.login(form)
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

/** 演示账号快捷填充（开发/测试环境种子账号，密码相同） */
const DEMO_ACCOUNTS = [
  { label: '管理员', username: 'admin', password: 'admin123' },
  { label: '教师', username: 'teacher1', password: 'admin123' },
  { label: '学生', username: 'student1', password: 'admin123' }
]

/** 快捷填充演示账号密码 */
function fillAccount(account) {
  form.username = account.username
  form.password = account.password
}

/** 一键启动直入系统：URL 带 auto=1&account=xxx&password=xxx 时自动登录（开发演示用） */
onMounted(() => {
  if (route.query.auto === '1' && route.query.account && route.query.password) {
    form.username = String(route.query.account)
    form.password = String(route.query.password)
    submit()
  }
})
</script>

<style scoped>
/* 页面底保持极浅灰纯色（规范第 13 节：禁止给页面背景用渐变） */
.login_page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
  background: var(--bg);
}

/* 登录卡：特大容器 r-xl 22px（规范第 5 节） */
.login_card {
  width: 100%;
  max-width: 400px;
  padding: 36px 32px 30px;
  background: var(--surface);
  border-radius: var(--r-xl);
  border: .5px solid var(--hair);
}

/* 品牌标识区：48px 渐变图标容器（渐变仅用于图标容器/主按钮，规范第 13 节） */
.login_brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.login_brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: 14px;
  border-radius: 14px;
  background: var(--brand-grad);
  color: #fff;
}

.login_card__title {
  margin: 0;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  text-align: center;
}

.login_card__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--g3);
  text-align: center;
}

/* 输入框统一 46px 高（规范 7.6） */
.login_card :deep(.el-input__wrapper) {
  height: 46px;
}

/* 主按钮：品牌渐变实心 + 品牌投影；hover 上浮 1.5px 投影加深（规范 7.2） */
.login_submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border: none;
  border-radius: var(--r-full);
  background-image: var(--brand-grad);
  box-shadow: var(--brand-shadow);
  font-size: 15px;
  letter-spacing: 4px;
}

.login_submit:hover:not(.is-disabled) {
  background-image: var(--brand-grad-hover);
  box-shadow: var(--brand-shadow-lg);
  transform: translateY(-1.5px);
}

.login_submit:active {
  transform: scale(.985);
}

/* 演示账号填充区：细分隔线标签 + 一行三枚次按钮（浅蓝描边，规范 7.2） */
.login_fills {
  margin-top: 18px;
}

.login_fills__label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 11.5px;
  color: var(--g4);
}

.login_fills__label::before,
.login_fills__label::after {
  content: '';
  flex: 1;
  height: .5px;
  background: var(--hair);
}

.login_fills__btns {
  display: flex;
  gap: 10px;
}

.login_fills__btn {
  flex: 1;
  height: 38px;
  margin-left: 0;
  border: .5px solid var(--brand-a32);
  border-radius: var(--r-full);
  background: transparent;
  color: var(--brand);
}

.login_fills__btn:hover {
  background: var(--brand-a08);
  border-color: var(--brand-a32);
  color: var(--brand);
}

.login_fills__btn:active {
  transform: scale(.98);
}

/* 页脚：演示环境提示（真实空态口径，规范 7.8） */
.login_footer {
  margin: 14px 0 0;
}

/* 表单标签样式由 element.css 全局统一（规范 7.6） */
</style>

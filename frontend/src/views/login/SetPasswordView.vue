<template>
  <div class="pwd_page">
    <div class="pwd_card">
      <div class="pwd_card__brand">
        <span class="pwd_card__mark">
          <el-icon><Lock /></el-icon>
        </span>
      </div>
      <h1 class="pwd_card__title">
        {{ forced ? '设置密码' : '修改密码' }}
      </h1>
      <p class="pwd_card__sub t-sub">
        <template v-if="store.passwordChangeRequired">
          密码已到期或从未设置，请设置新密码后继续使用
        </template>
        <template v-else-if="store.needSetPassword">
          首次登录须设置敏感操作密码，用于审批等敏感操作二次确认
        </template>
        <template v-else>
          8-64 位，需同时包含字母和数字
        </template>
      </p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <!-- 首次设置（无密码散列）免旧密码 -->
        <el-form-item
          v-if="!store.needSetPassword"
          label="旧密码"
          prop="oldPassword"
        >
          <el-input
            v-model="form.oldPassword"
            type="password"
            placeholder="请输入旧密码"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item
          label="新密码"
          prop="newPassword"
        >
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="8-64 位，含字母和数字"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item
          label="确认新密码"
          prop="confirmPassword"
        >
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            size="large"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-button
          class="pwd_submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
        >
          确 定
        </el-button>
      </el-form>

      <!-- 强制改密期间允许放弃并退回登录页，避免被困在本页 -->
      <div class="pwd_footer">
        <button
          type="button"
          class="pwd_logout"
          :disabled="loading"
          @click="quit"
        >
          退出登录
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { updatePassword } from '@/api/user'

const router = useRouter()
const store = useUserStore()

const forced = computed(() => store.mustSetPassword)

const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/,
      message: '新密码需 8-64 位且同时包含字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        callback(value === form.newPassword ? undefined : new Error('两次输入的密码不一致'))
      },
      trigger: 'blur'
    }
  ]
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await updatePassword({
      oldPassword: store.needSetPassword ? undefined : form.oldPassword,
      newPassword: form.newPassword
    })
    store.clearPasswordFlags()
    ElMessage.success('密码已更新')
    router.push('/')
  } finally {
    loading.value = false
  }
}

/** 放弃本次强制改密：注销会话回登录页 */
async function quit() {
  loading.value = true
  try {
    await store.logout()
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pwd_page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
  background:
    radial-gradient(900px 500px at 80% 10%, var(--brand-a12), transparent 60%),
    var(--bg);
}

.pwd_card {
  width: 100%;
  max-width: 400px;
  padding: 34px 32px 30px;
  background: var(--surface);
  border-radius: var(--r-xl);
  border: .5px solid var(--hair);
  box-shadow: 0 24px 64px -28px rgba(15, 30, 60, .18);
}

.pwd_card__brand {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.pwd_card__mark {
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

.pwd_card :deep(.el-input__wrapper) {
  min-height: 46px;
}

.pwd_card__title {
  margin: 0;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  text-align: center;
}

.pwd_card__sub {
  margin: 6px 0 22px;
  text-align: center;
}

.pwd_submit {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border-radius: var(--r-full);
  font-size: 15px;
  letter-spacing: 4px;
}

.pwd_submit:active {
  transform: scale(.985);
}

.pwd_footer {
  margin-top: 14px;
  text-align: center;
}

.pwd_logout {
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--g3);
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--r-full);
  transition: all var(--dur-fast) var(--ease);
}

.pwd_logout:hover:not(:disabled) {
  color: var(--g1);
  background: var(--brand-a04);
}

.pwd_logout:disabled {
  opacity: .5;
  cursor: not-allowed;
}
</style>

<template>
  <div class="login_page">
    <div class="login_card">
      <h1 class="login_card__title">
        实验室设备管理系统
      </h1>
      <p class="login_card__sub t-sub">
        管理员统一入口
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
          prop="username"
        >
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
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

        <el-button
          class="login_submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
        >
          登 录
        </el-button>
        <el-button
          class="login_admin_fill"
          size="large"
          @click="fillAdmin"
        >
          管理员账号填充
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

/** 快捷填充管理员账号密码 */
function fillAdmin() {
  form.username = 'admin'
  form.password = 'admin123'
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

.login_admin_fill {
  width: 100%;
  height: 42px;
  margin-top: 10px;
  margin-left: 0;
  border-radius: var(--r-full);
}

/* el-button 相邻默认 12px 左边距归零，保持整宽 */
</style>

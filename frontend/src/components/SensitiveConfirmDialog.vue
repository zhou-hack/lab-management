<template>
  <!-- 敏感操作二次密码确认：确认成功后 Sa-Token 二级认证在 validSeconds 内有效 -->
  <el-dialog
    :model-value="modelValue"
    title="敏感操作确认"
    width="380px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="password = ''"
  >
    <p class="confirm_hint t-hint">
      该操作需要二次确认，请输入登录密码
    </p>
    <el-input
      v-model="password"
      type="password"
      placeholder="请输入密码"
      size="large"
      show-password
      @keyup.enter="submit"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        取 消
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="submit"
      >
        确 认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmPassword } from '@/api/user'

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'confirmed'])

const password = ref('')
const loading = ref(false)

async function submit() {
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    const res = await confirmPassword(password.value)
    emit('update:modelValue', false)
    // 透传二级认证有效期（秒），调用方继续原敏感操作
    emit('confirmed', res.validSeconds)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.confirm_hint {
  margin: 0 0 12px;
}
</style>

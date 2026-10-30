import request from '../request'

export function pageAssistants(params) {
  return request.get('/item/assistants', { params })
}

export function createAssistant(data) {
  return request.post('/item/assistants', data)
}

export function revokeAssistant(id) {
  return request.post(`/item/assistants/${String(id)}/revoke`)
}

export function searchAssistantStudents(keyword) {
  return request.get('/item/assistants/student_options', {
    params: { keyword: keyword || undefined }
  })
}

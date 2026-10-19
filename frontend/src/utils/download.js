/**
 * 文件下载工具：
 * - 优先解析后端 Content-Disposition 的 RFC 5987 UTF-8 文件名（支持中文）
 * - 兼容 filename="xxx" 普通形式，均失败时用调用方提供的兜底名
 * - saveBlobFile 接收 { blob, fileName }（request 拦截器 blob 分支的返回结构）
 */
export function resolveFileName(disposition, fallbackName) {
  if (disposition) {
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    if (utf8Match) {
      try {
        return decodeURIComponent(utf8Match[1])
      } catch {
        // 编码异常时走普通解析
      }
    }
    const plainMatch = /filename="?([^";]+)"?/i.exec(disposition)
    if (plainMatch) {
      return plainMatch[1]
    }
  }
  return fallbackName
}

export function saveBlobFile(file, fallbackName) {
  const blob = file instanceof Blob ? file : file.blob
  const fileName = (file && file.fileName) || fallbackName || 'download'
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

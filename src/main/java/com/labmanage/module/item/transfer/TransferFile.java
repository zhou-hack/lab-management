package com.labmanage.module.item.transfer;

/**
 * 待下载文件的内存载体（导出 / 模板统一返回结构）
 * <p>
 * 生成完整字节后才写 HTTP 响应，保证异常发生在响应提交之前，可正常返回 JSON 错误体。
 */
public record TransferFile(String fileName, String contentType, byte[] content) {
}

export const errors = {
  requestFailed: "请求失败（{{status}}）",
  NOT_FOUND: "未找到",
  FORBIDDEN: "没有权限访问该资源",
  UNAUTHORIZED: "未登录或登录已过期",
  TOO_MANY_REQUESTS: "请求过于频繁，请稍后再试",
  INVALID_PARAMETER: "参数无效",
  CONFLICT: "操作冲突",
  FILE_TOO_LARGE: "文件过大",
  STORAGE_ERROR: "存储错误",
  INTERNAL_ERROR: "服务器内部错误",
} as const

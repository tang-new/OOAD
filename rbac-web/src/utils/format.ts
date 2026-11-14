/** 复用一期通用展示逻辑；不再包含公司、数据范围、热力图语义。 */
export const splitPermissionCode = (code: string): string[] => code.split(':')
/** 审计过滤以 UTC ISO8601 传输，绝不发送无时区的本地时间。 */
export const toInstant = (value: string): string | undefined => value ? new Date(value).toISOString() : undefined

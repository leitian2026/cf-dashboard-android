package com.leitian.cfdashboard.data

import org.json.JSONObject

/**
 * 把"读出来的绑定"还原成可以重新提交的绑定。
 * Cloudflare 读取设置时，Secret 类绑定只返回名字和类型、不返回值；
 * 原样提交会报 "invalid or missing text property for binding XXX"。
 * 这类绑定改成 inherit（沿用上一版本里的值），值保持不变也不会丢。其他类型原样带回。
 */
internal fun bindingForResubmit(raw: JSONObject): JSONObject {
    val type = raw.optString("type")
    return if (type == "secret_text" || type == "secret_key") {
        JSONObject().put("type", "inherit").put("name", raw.optString("name"))
    } else raw
}

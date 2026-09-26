package com.leitian.cfdashboard.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

// 之前所有请求都是 client.newCall(req).execute()，即阻塞调用。
// 协程被 cancel()（比如快速切换 Worker 时上一个请求被取消）时，execute() 完全不知情，
// 会继续跑到底占着 OkHttp 的连接/线程槽位——这就是连续点开好几个 Worker 后页面转圈圈转不停的根因：
// 旧请求赖着不走，新请求在 dispatcher 队列里排在后面，迟迟轮不到。
// 用 enqueue + suspendCancellableCoroutine 包一层，协程一取消就真的把底层 Call.cancel() 掉。
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
        try { cancel() } catch (_: Throwable) {}
    }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resumeWith(Result.success(response))
        }
    })
}

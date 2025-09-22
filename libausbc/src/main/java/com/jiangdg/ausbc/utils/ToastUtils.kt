
package com.jiangdg.ausbc.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread

/** Simple Toast
 *
 * @author Created by jiangdongguo on 2022/1/21
 */
object ToastUtils {

    private var applicationCtx: Context ?= null

    @MainThread
    fun init(ctx: Context) {
        if (applicationCtx != null) {
            return
        }
        this.applicationCtx = ctx.applicationContext
    }

    @JvmStatic
    fun show(msg: String) {
        applicationCtx?.let { ctx ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    @JvmStatic
    fun show(resId: Int) {
        applicationCtx?.let { ctx ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_LONG).show()
            }
        }
    }

}
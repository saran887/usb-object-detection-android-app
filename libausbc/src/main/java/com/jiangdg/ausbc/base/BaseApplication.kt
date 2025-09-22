
package com.jiangdg.ausbc.base

import android.app.Application
import com.jiangdg.ausbc.utils.CrashUtils
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.ToastUtils

/** Base Application
 *
 * @author Created by jiangdg on 2022/2/28
 */
open class BaseApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        CrashUtils.init(this)
        Logger.init(this)
        ToastUtils.init(this)
    }
}
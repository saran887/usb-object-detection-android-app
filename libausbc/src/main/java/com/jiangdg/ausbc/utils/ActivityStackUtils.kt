
package com.jiangdg.ausbc.utils

import android.app.Activity
import java.util.*

/** Activity stack manager
 *
 * @author Created by jiangdg on 2022/3/1
 */
object ActivityStackUtils {
    private const val TAG = "ActivityStackUtils"
    private val mStack: Stack<Activity> = Stack()

    fun pushActivity(activity: Activity) {
        mStack.push(activity)
        Logger.d(TAG, "push stack: ${activity.localClassName}")
    }

    fun popActivity() {
        if (!mStack.empty()) {
            val activity: Activity = mStack.pop()
            activity.finish()
            Logger.d(TAG, "pop stack: ${activity.localClassName}")
        }
    }

    fun removeActivity(activity: Activity) {
        if (!mStack.empty()) {
            mStack.remove(activity)
            Logger.d(TAG, "remove stack: ${activity.localClassName}")
        }
    }

    fun getStackTop(): Activity? {
        var activity: Activity? = null
        if (!mStack.empty()) {
            activity = mStack.peek()
            Logger.d(TAG, "stack top: ${activity.localClassName}")
        }
        return activity
    }

    fun popAllActivity() {
        if (!mStack.empty()) {
            val size: Int = mStack.size
            for (i in 0 until size) {
                popActivity()
            }
        }
    }

    fun hasActivity() = !mStack.isEmpty()
}
/*
 *

 */
package com.jiangdg.ausbc.base

import android.app.Dialog

interface DialogInterface {
    fun getDialog(): Dialog

    fun show()

    fun dismiss()

    fun isShowing(): Boolean

    fun setCanceledOnTouchOutside(cancel: Boolean)

    fun setCancelable(flag: Boolean)
}
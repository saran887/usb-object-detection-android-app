
package com.jiangdg.ausbc.callback

/** Capture a media callback
 *
 * @author Created by jiangdg on 2022/1/29
 */
interface ICaptureCallBack {
    fun onBegin()
    fun onError(error: String?)
    fun onComplete(path: String?)
}
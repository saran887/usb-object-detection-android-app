
package com.jiangdg.ausbc.callback

/** Play media callback
 *
 * @author Created by jiangdg on 2022/2/09
 */
interface IPlayCallBack {
    fun onBegin()
    fun onError(error: String)
    fun onComplete()
}
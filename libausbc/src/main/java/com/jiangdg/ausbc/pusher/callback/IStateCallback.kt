
package com.jiangdg.ausbc.pusher.callback

/** Push stream status callback interface
*
 * @author Created by jiangdg on 2023/1/29
 */
interface IStateCallback {
    fun onPushState(code: Int, msg: String?)
}
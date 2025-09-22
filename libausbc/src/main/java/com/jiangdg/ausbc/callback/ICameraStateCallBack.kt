
package com.jiangdg.ausbc.callback

import com.jiangdg.ausbc.MultiCameraClient

/** camera operator state
 *
 * @author Created by jiangdg on 2022/2/09
 */
interface ICameraStateCallBack {
    fun onCameraState(self: MultiCameraClient.ICamera, code: State, msg: String? = null)

    enum class State {
        OPENED, CLOSED, ERROR
    }
}
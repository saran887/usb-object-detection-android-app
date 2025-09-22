
package com.jiangdg.ausbc.pusher.aliyun

import android.content.Context
import com.jiangdg.ausbc.pusher.IPusher
import com.jiangdg.ausbc.pusher.callback.IStateCallback
import com.jiangdg.ausbc.pusher.config.AusbcConfig

/** Your self pusher engine
 *
 * @author Created by jiangdg on 2023/1/29
 */
class AliyunPusher: IPusher {
    override fun init(context: Context?, ausbcConfig: AusbcConfig?, callback: IStateCallback?) {
        TODO("Not yet implemented")
    }

    override fun start(url: String?) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }

    override fun reconnect() {
        TODO("Not yet implemented")
    }

    override fun reconnectUrl(url: String?) {
        TODO("Not yet implemented")
    }

    override fun pushStream(type: Int, data: ByteArray?, size: Int, pts: Long) {
        TODO("Not yet implemented")
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override fun isPushing(): Boolean {
        TODO("Not yet implemented")
    }
}

package com.jiangdg.ausbc.pusher
import android.content.Context
import com.jiangdg.ausbc.pusher.aliyun.AliyunPusher
import com.jiangdg.ausbc.pusher.callback.IStateCallback
import com.jiangdg.ausbc.pusher.config.AusbcConfig

/**External interface of streaming module
 *
 * @author Created by jiangdg on 2023/1/12
 */
object AusbcPusher {
    private var mPusher: IPusher? =  null

    /**Initialize streaming engine
     *
     *@ param context context
     */
    fun init(context: Context?, ausbcConfig: AusbcConfig, callback: IStateCallback?) {
        mPusher = ausbcConfig.getPusher() ?:  AliyunPusher()
        mPusher?. init(context, ausbcConfig, callback)
    }

    /**Start streaming
     *
     *@ param url Streaming media server URL
     */
    fun start(url: String?) {
        if (isPushing()) {
            mPusher?. stop()
        }
        mPusher?. start(url)
    }

    /**
     *Stop pushing flow
     */
    fun stop() {
        mPusher?. stop()
    }

    /**
     *Pause streaming
     */
    fun pause() {
        mPusher?. pause()
    }

    /**
     *Resume streaming
     */
    fun resume() {
        mPusher?. resume()
    }

    /**
     *Reconnection
     */
    fun reconnect() {
        mPusher?. reconnect()
    }

    /**Reconnect, support switching urls
     *
     *@ param url Reconnect url
     */
    fun reconnectUrl(url: String?) {
        mPusher?. reconnectUrl(url)
    }

    /**
     *Destroy streaming engine
     */
    fun destroy() {
        mPusher?. destroy()
    }

    /**Push flow
     *
     *@ param type 0: Audio 1: Video
     *@ param data audio and video data
     *@ param size audio and video data size
     *@ param pts timestamp
     */
    fun pushStream(type: Int, data: ByteArray?, size: Int, pts: Long) {
        mPusher?. pushStream(type, data, size, pts)
    }

    /**Streaming state
     *
     *@ return true streaming
     */
    fun isPushing(): Boolean {
        return mPusher?. isPushing() == true
    }
}
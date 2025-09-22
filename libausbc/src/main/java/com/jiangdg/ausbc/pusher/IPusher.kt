
package com.jiangdg.ausbc.pusher
import android.content.Context
import com.jiangdg.ausbc.pusher.callback.IStateCallback
import com.jiangdg.ausbc.pusher.config.AusbcConfig

/**The push module policy interface supports the extension of other third-party SDKs
 *
 * @author Created by jiangdg on 2023/1/29
 */
interface IPusher {
    
    /**Initialize streaming engine
     *
     *@param context context
     *@param callback streaming status callback interface
     */
    fun init(context: Context?, ausbcConfig: AusbcConfig?, callback: IStateCallback?)
    
    /**Start streaming
     *
     *@param url Streaming media server URL
     */
    fun start(url: String?)
    
    /**
     *Stop pushing flow
     */
    fun stop()

    /**
     *Pause streaming
     */
    fun pause()

    /**
     *Resume streaming
     */
    fun resume()

    /**
     *Reconnection
     */
    fun reconnect()

    /**Reconnect, support switching urls
     *
     *@param url Reconnect url
     */
    fun reconnectUrl(url: String?)

    /**Push flow
     *
     *@param type 0: video 1: audio
     *@param data audio and video data
     *@param size audio and video data size
     *@param pts timestamp
     */
    fun pushStream(type: Int, data: ByteArray?, size: Int, pts: Long)

    /**
     *Destroy streaming engine
     */
    fun destroy()

    /**Successful streaming flag
     *
     *@ return true is streaming
     */
    fun isPushing(): Boolean
}
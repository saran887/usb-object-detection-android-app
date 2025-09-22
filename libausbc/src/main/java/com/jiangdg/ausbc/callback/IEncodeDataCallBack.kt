
package com.jiangdg.ausbc.callback

import java.nio.ByteBuffer

/** Encode data callback
 *
 * @author Created by jiangdg on 2022/1/29
 */
interface IEncodeDataCallBack {
    fun onEncodeData(type: DataType, buffer:ByteBuffer, offset: Int, size: Int, timestamp: Long)
    enum class DataType {
        AAC,       // aac without ADTS header,
                   // if want adding adts, should call MediaUtils.addADTStoPacket() method
        H264_KEY,  // H.264, key frame
        H264_SPS,  // H.264, sps & pps
        H264       // H.264 not key frame
    }
}
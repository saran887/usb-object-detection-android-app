
package com.jiangdg.ausbc.callback

/** Camera preview data callback
 *
 * @author Created by jiangdg on 2022/1/29
 */
interface IPreviewDataCallBack {
    fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: DataFormat)

    enum class DataFormat {
        NV21, RGBA
    }
}
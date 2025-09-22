
package com.jiangdg.ausbc.encode.audio

import com.jiangdg.ausbc.encode.bean.RawData

/** Audio(pcm) collection context
 *
 * @author Created by jiangdg on 2022/9/14
 */
interface IAudioStrategy {
    fun initAudioRecord()
    fun startRecording()
    fun stopRecording()
    fun releaseAudioRecord()
    fun read(): RawData?
    fun isRecording(): Boolean
    fun getSampleRate(): Int
    fun getAudioFormat(): Int
    fun getChannelCount(): Int
    fun getChannelConfig(): Int
}
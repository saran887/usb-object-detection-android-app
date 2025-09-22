
package com.jiangdg.ausbc.render.effect

import android.content.Context
import android.opengl.GLES20
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.effect.bean.CameraEffect

/** Zoom effect
 *
 * @author Created by jiangdg on 2022/01/28
 */
class EffectZoom(context: Context) : AbstractEffect(context) {

    private var mTimeStampsHandler = -1
    private var mTimeCount = 0

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CameraEffect.CLASSIFY_ID_ANIMATION

    override fun init() {
        mTimeStampsHandler = GLES20.glGetUniformLocation(mProgram, "timeStamps")
    }

    override fun beforeDraw() {
        if (mTimeCount > 65535) {
            mTimeCount = 0
        }
        GLES20.glUniform1f(mTimeStampsHandler, (++mTimeCount % 9).toFloat())
    }

    override fun getVertexSourceId(): Int = R.raw.effect_zoom_vertex

    override fun getFragmentSourceId(): Int  = R.raw.base_fragment

    companion object {
        const val ID = 300
    }
}
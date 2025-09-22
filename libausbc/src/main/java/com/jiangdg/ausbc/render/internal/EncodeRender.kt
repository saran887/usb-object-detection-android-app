/*
 *

 */
package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.opengl.EGLContext
import android.view.Surface
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.env.EGLEvn


class EncodeRender(context: Context): AbstractRender(context) {

    private var mEgl: EGLEvn? = null

    fun initEGLEvn(glContext: EGLContext) {
        mEgl = EGLEvn()
        mEgl?.initEgl(glContext)
    }

    fun setupSurface(surface: Surface) {
        mEgl?.setupSurface(surface)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}
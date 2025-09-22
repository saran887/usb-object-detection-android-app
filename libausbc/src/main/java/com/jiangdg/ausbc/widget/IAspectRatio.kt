
package com.jiangdg.ausbc.widget

import android.view.Surface

/** aspect ratio setting func interface
 *
 * @author Created by jiangdg on 2022/1/26
 */
interface IAspectRatio {
    fun setAspectRatio(width: Int, height: Int)
    fun getSurfaceWidth(): Int
    fun getSurfaceHeight(): Int
    fun getSurface(): Surface?
    fun postUITask(task: ()->Unit)
}
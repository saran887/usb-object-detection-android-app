
package com.jiangdg.ausbc.render.effect

import android.content.Context
import com.jiangdg.ausbc.render.internal.AbstractFboRender

/** abstract effect class, extended from AbstractFboRender
 *
 * @author Created by jiangdg on 2022/1/26
 */
abstract class AbstractEffect(ctx: Context) : AbstractFboRender(ctx) {

    /**
     * Get effect id
     *
     * @return effect id
     */
    abstract fun getId(): Int

    /**
     * Get classify id
     *
     * @return effect classify id
     */
    abstract fun getClassifyId(): Int
}
/*
 *

 */
package com.jiangdg.ausbc.render.env

/** rotate angle type
 *
 * @author Created by jiangdg on 2021/12/28
 */
enum class RotateType {
    ANGLE_0,        // default, do nothing
    ANGLE_90,
    ANGLE_180,
    ANGLE_270,
    FLIP_UP_DOWN,    // flip vertically
    FLIP_LEFT_RIGHT  // horizontal flip(mirror)
}

package com.jiangdg.ausbc.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jiangdg.ausbc.utils.ActivityStackUtils


abstract class BaseActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getRootView(layoutInflater))
        initView()
        initData()
        ActivityStackUtils.pushActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clear()
        ActivityStackUtils.removeActivity(this)
    }

    protected abstract fun getRootView(layoutInflater: LayoutInflater): View?
    protected open fun initView() {}
    protected open fun initData() {}
    protected open fun clear() {}
}
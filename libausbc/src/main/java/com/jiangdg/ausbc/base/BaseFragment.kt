
package com.jiangdg.ausbc.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/** Base fragment
 *
 * @author Created by jiangdg on 2022/1/21
 */
abstract class BaseFragment: Fragment() {

    private var mRootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return getRootView(inflater, container).apply {
            mRootView = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clear()
        mRootView = null
    }

    open fun isFragmentAttached(): Boolean {
        return if (null == activity || requireActivity().isDestroyed) {
            false
        } else isAdded && !isDetached
    }

    protected fun getRootView() = mRootView

    protected abstract fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View?
    protected open fun initView() {}
    protected open fun initData() {}
    protected open fun clear() {}
}
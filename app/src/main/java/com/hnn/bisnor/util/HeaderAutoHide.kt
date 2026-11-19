package com.hnn.bisnor.util

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

/**
 * Collapses everything above [content] (title, search, chips…) while a list scrolls down
 * and brings it back on scroll up or at the top.
 */
object HeaderAutoHide {
    fun attach(root: ViewGroup, content: View, lists: List<RecyclerView>) {
        var hidden = false
        fun headers() = (0 until root.childCount).map { root.getChildAt(it) }.takeWhile { it !== content }
        fun setHidden(h: Boolean) {
            if (h == hidden) return
            hidden = h
            TransitionManager.beginDelayedTransition(root, AutoTransition().setDuration(180))
            headers().forEach { it.visibility = if (h) View.GONE else View.VISIBLE }
        }
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(-1)) setHidden(false)
                else if (dy > 12 && rv.canScrollVertically(1)) setHidden(true)
                else if (dy < -12) setHidden(false)
            }
        }
        lists.forEach { it.addOnScrollListener(listener) }
    }
}

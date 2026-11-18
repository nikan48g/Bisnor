package com.hnn.bisnor.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hnn.bisnor.R

/**
 * Apple-style action sheet: optional poster + title header, one rounded card of
 * icon rows (destructive rows in red) and a separate "cancel" capsule.
 */
object ActionSheet {

    data class Action(
        val icon: Int,
        val label: String,
        val destructive: Boolean = false,
        val run: () -> Unit
    )

    fun show(
        context: Context,
        title: String,
        subtitle: String? = null,
        imageUrl: String? = null,
        actions: List<Action>
    ) {
        val dialog = BottomSheetDialog(context)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val onSurface = ContextCompat.getColor(context, R.color.on_surface)
        val dim = ContextCompat.getColor(context, R.color.on_surface_variant)
        val danger = ContextCompat.getColor(context, R.color.danger)
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold)
        val medium = ResourcesCompat.getFont(context, R.font.vazirmatn_medium)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(20))
        }

        // grab handle
        root.addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(ContextCompat.getColor(context, R.color.outline))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
        })

        // header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(14))
        }
        if (!imageUrl.isNullOrBlank()) {
            header.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(ImageUrlHelper.getOptimizedImageUrl(imageUrl)) {
                    transformations(RoundedCornersTransformation(dp(10).toFloat()))
                }
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(66)).apply { marginEnd = dp(12) }
            })
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            text = title
            typeface = bold
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                typeface = medium
                setTextColor(dim)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(2), 0, 0)
            })
        }
        header.addView(texts)
        root.addView(header)

        // actions card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_surface)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_container_high))
            clipToOutline = true
        }
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                card.addView(View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.outline_variant))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(52)
                    }
                })
            }
            val tint = if (action.destructive) danger else onSurface
            card.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = ContextCompat.getDrawable(context, outValue.resourceId)
                addView(ImageView(context).apply {
                    setImageResource(action.icon)
                    imageTintList = ColorStateList.valueOf(tint)
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(14) }
                })
                addView(TextView(context).apply {
                    text = action.label
                    typeface = medium
                    setTextColor(tint)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
                setOnClickListener {
                    dialog.dismiss()
                    action.run()
                }
            })
        }
        root.addView(card)

        // cancel capsule
        root.addView(TextView(context).apply {
            text = "انصراف"
            typeface = bold
            gravity = Gravity.CENTER
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setBackgroundResource(R.drawable.bg_card_surface)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_container_high))
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(10)
            }
        })

        dialog.setContentView(root)
        dialog.show()
    }
}

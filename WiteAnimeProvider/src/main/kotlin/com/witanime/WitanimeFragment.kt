package com.witanime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class WitanimeFragment(private val plugin: WitanimePlugin) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 32, 32, 32)
        }

        // Add title
        val titleTextView = TextView(context).apply {
            text = "WitAnime Provider"
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        linearLayout.addView(titleTextView)

        // Add description
        val descriptionTextView = TextView(context).apply {
            text = "مزود محتوى الأنمي العربي من موقع WitAnime"
            textSize = 16f
            setPadding(0, 16, 0, 16)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        linearLayout.addView(descriptionTextView)

        // Add features
        val featuresTextView = TextView(context).apply {
            text = """
                الميزات:
                • دعم الأنميات المترجمة للعربية
                • أفلام الأنمي والـ OVA
                • جودات متعددة للمشاهدة
                • روابط التحميل المباشر
                • البحث في المحتوى
                • صفحات رئيسية متنوعة
            """.trimIndent()
            textSize = 14f
            setPadding(0, 16, 0, 16)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        linearLayout.addView(featuresTextView)

        // Add version info
        val versionTextView = TextView(context).apply {
            text = "الإصدار: 1.0.0"
            textSize = 12f
            alpha = 0.7f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        linearLayout.addView(versionTextView)

        // Add website info
        val websiteTextView = TextView(context).apply {
            text = "الموقع: https://witanime.uno"
            textSize = 12f
            alpha = 0.7f
            setPadding(0, 8, 0, 0)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        linearLayout.addView(websiteTextView)

        return linearLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Additional setup if needed
    }
}
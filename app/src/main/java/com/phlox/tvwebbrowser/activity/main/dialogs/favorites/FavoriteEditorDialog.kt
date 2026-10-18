package com.phlox.tvwebbrowser.activity.main.dialogs.favorites

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.domain.UrlNormalizer

/**
 * Created by PDT on 13.09.2016.
 *
 * A form: it shows the title/url it was opened with and reports the edited values. It deliberately
 * knows nothing about the bookmark behind them — the owner applies the values and persists them
 * through its model.
 */
class FavoriteEditorDialog(
    context: Context,
    private val initialTitle: String?,
    private val initialUrl: String?,
    private val newBookmark: Boolean,
    private val callback: Callback,
) : Dialog(context) {
    private val tvTitle: TextView
    private val tvUrl: TextView
    private val etTitle: EditText
    private val etUrl: EditText
    private val ibTitle: ImageButton
    private val ibUrl: ImageButton
    private val btnDone: Button
    private val btnCancel: Button

    fun interface Callback {
        /** The edited values, with the url already made absolute. */
        fun onDone(
            title: String,
            url: String,
        )
    }

    init {
        setCancelable(true)
        setTitle(if (newBookmark) R.string.new_bookmark else R.string.edit)
        setContentView(R.layout.dialog_new_favorite_item)
        findViewById<TextView>(R.id.tvDialogTitle).text =
            context.getString(if (newBookmark) R.string.new_bookmark else R.string.edit)
        tvTitle = findViewById<View>(R.id.tvTitle) as TextView
        tvUrl = findViewById<View>(R.id.tvUrl) as TextView
        etTitle = findViewById<View>(R.id.etTitle) as EditText
        etUrl = findViewById<View>(R.id.etUrl) as EditText
        ibTitle = findViewById<View>(R.id.ibTitle) as ImageButton
        ibUrl = findViewById<View>(R.id.ibUrl) as ImageButton
        btnDone = findViewById<View>(R.id.btnDone) as Button
        btnCancel = findViewById<View>(R.id.btnCancel) as Button

        ibTitle.setOnClickListener {
            ibTitle.visibility = View.GONE
            tvTitle.visibility = View.GONE
            etTitle.visibility = View.VISIBLE
            etTitle.requestFocus()
        }

        ibUrl.setOnClickListener {
            ibUrl.visibility = View.GONE
            tvUrl.visibility = View.GONE
            etUrl.visibility = View.VISIBLE
            etUrl.requestFocus()
        }

        btnDone.setOnClickListener {
            callback.onDone(
                etTitle.text.toString(),
                UrlNormalizer.withAbsoluteScheme(etUrl.text.toString()),
            )
            dismiss()
        }
        btnCancel.setOnClickListener { dismiss() }

        tvTitle.text = initialTitle
        etTitle.setText(initialTitle)
        tvUrl.text = initialUrl
        etUrl.setText(initialUrl)
    }
}

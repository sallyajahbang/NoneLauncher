// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Placeholder UI. Diganti dengan halaman launcher saat milestone 1 dimulai.
        val text = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)
    }
}

package com.tanglish.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        box.addView(TextView(this).apply { text = "Tanglish Keyboard"; textSize = 24f })

        box.addView(TextView(this).apply { text = "\nStep 1: Anthropic API key"; textSize = 16f })
        val keyField = EditText(this).apply {
            hint = "sk-ant-..."
            setText(prefs.getString("key", ""))
            setSingleLine()
        }
        box.addView(keyField)
        box.addView(Button(this).apply {
            text = "Save key"
            setOnClickListener {
                prefs.edit().putString("key", keyField.text.toString().trim()).apply()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })

        box.addView(TextView(this).apply { text = "\nStep 2: Keyboard enable pannunga"; textSize = 16f })
        box.addView(Button(this).apply {
            text = "Open keyboard settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })

        box.addView(TextView(this).apply { text = "\nStep 3: Keyboard select pannunga"; textSize = 16f })
        box.addView(Button(this).apply {
            text = "Choose keyboard"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })

        box.addView(TextView(this).apply { text = "\nTest pannunga:"; textSize = 16f })
        box.addView(EditText(this).apply { hint = "inga Tanglish type pannunga, Translate press pannunga" })

        setContentView(ScrollView(this).apply { addView(box) })
    }
}

package com.hqd.quickanswer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNotificationPermissionIfNeeded()
        NotificationHelper.ensureChannel(this)
    }

    private fun buildUi() {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "Quick AI"
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Nhập câu hỏi ngay trên thanh thông báo → Gemini → trả đáp án gọn."
            textSize = 15f
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(subtitle)

        apiKeyInput = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(SecurePrefs(this@MainActivity).getApiKey())
        }
        root.addView(apiKeyInput, fieldParams())

        modelInput = EditText(this).apply {
            hint = "Model"
            setSingleLine(true)
            setText(SecurePrefs(this@MainActivity).getModel())
        }
        root.addView(modelInput, fieldParams())

        val saveButton = Button(this).apply {
            text = "LƯU & BẬT TRỢ LÝ"
            setOnClickListener { saveAndEnableNotification() }
        }
        root.addView(saveButton, buttonParams())

        val testButton = Button(this).apply {
            text = "TEST API"
            setOnClickListener { testApi() }
        }
        root.addView(testButton, buttonParams())

        val guide = TextView(this).apply {
            text = "Cách dùng trên Samsung A56:\n1) Bấm LƯU & BẬT.\n2) Kéo thanh thông báo.\n3) Nhấn “Nhập câu hỏi”.\n4) Dán câu hỏi A/B/C/D hoặc Đúng/Sai.\n5) Gửi. Kết quả sẽ hiện lại trên notification.\n\nLưu ý: API key được mã hóa bằng Android Keystore và lưu cục bộ trên máy. Ứng dụng không tự đọc clipboard nền."
            textSize = 15f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(guide, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun saveAndEnableNotification() {
        val api = apiKeyInput.text.toString().trim()
        val model = modelInput.text.toString().trim().ifBlank { SecurePrefs.DEFAULT_MODEL }
        if (api.isBlank()) {
            apiKeyInput.error = "Nhập Gemini API key"
            return
        }

        SecurePrefs(this).apply {
            saveApiKey(api)
            saveModel(model)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission chưa được cấp nên notification sẽ không hiện được.
            // Xin quyền ngay; showAssistant() sẽ được gọi lại trong onRequestPermissionsResult nếu người dùng đồng ý.
            Toast.makeText(
                this,
                "Đã lưu cài đặt. Cần cấp quyền THÔNG BÁO để bật trợ lý.",
                Toast.LENGTH_LONG
            ).show()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            return
        }

        if (NotificationHelper.showAssistant(this)) {
            Toast.makeText(this, "Đã lưu và bật Quick AI.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Đã lưu cài đặt nhưng thông báo của app đang bị tắt. Vào Cài đặt > Ứng dụng > Quick AI > Thông báo để bật.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Người dùng vừa đồng ý cấp quyền: nếu đã có API key thì bật thông báo trợ lý ngay.
                if (SecurePrefs(this).getApiKey().isNotBlank()) {
                    NotificationHelper.showAssistant(this)
                    Toast.makeText(this, "Đã bật Quick AI.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "Chưa cấp quyền THÔNG BÁO nên trợ lý không thể hiện thông báo. Vào Cài đặt > Ứng dụng > Quick AI > Thông báo để bật.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun testApi() {
        val api = apiKeyInput.text.toString().trim()
        val model = modelInput.text.toString().trim().ifBlank { SecurePrefs.DEFAULT_MODEL }
        if (api.isBlank()) {
            apiKeyInput.error = "Nhập Gemini API key"
            return
        }

        Toast.makeText(this, "Đang test…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val answer = AiClient.generateAnswer(api, model, "2 + 2 bằng bao nhiêu? A. 3 B. 4 C. 5 D. 6")
                runOnUiThread { Toast.makeText(this, answer, Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Test lỗi: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) }
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

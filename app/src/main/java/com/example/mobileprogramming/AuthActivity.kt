package com.example.mobileprogramming

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mobileprogramming.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Check if user is already logged in (including guest)
        if (SecurityHelper.isLoggedIn(this)) {
            navigateToMain()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Setup Selector Event Listeners
        binding.btnTriggerLogin.setOnClickListener {
            showView(binding.layoutLoginForm)
        }

        binding.btnTriggerRegister.setOnClickListener {
            showView(binding.layoutRegisterForm)
        }

        binding.btnStartGuest.setOnClickListener {
            // Log in as Guest (username = guest, nickname = 게스트, grade = 비회원)
            SecurityHelper.setLoggedIn(this, true, "guest", "게스트", "비회원")
            Toast.makeText(this, "비회원으로 시작합니다.", Toast.LENGTH_SHORT).show()
            navigateToMain()
        }

        // 3. Back buttons
        binding.btnBackToSelectorFromLogin.setOnClickListener {
            showView(binding.layoutSelector)
        }

        binding.btnBackToSelectorFromRegister.setOnClickListener {
            showView(binding.layoutSelector)
        }

        // 4. Submit Login
        binding.btnSubmitLogin.setOnClickListener {
            val username = binding.etLoginUsername.text.toString().trim()
            val password = binding.etLoginPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = SecurityHelper.authenticateUser(this, username, password)
            if (success) {
                val nickname = SecurityHelper.getNickname(this)
                Toast.makeText(this, "$nickname 님, 환영합니다!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            } else {
                Toast.makeText(this, "아이디 또는 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Submit Register
        binding.btnSubmitRegister.setOnClickListener {
            val username = binding.etRegisterUsername.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString().trim()
            val nickname = binding.etRegisterNickname.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.contains(":") || username.contains("|") || password.contains(":") || password.contains("|") || nickname.contains(":") || nickname.contains("|")) {
                Toast.makeText(this, "아이디, 비밀번호, 닉네임에는 ':' 이나 '|' 기호를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = SecurityHelper.registerUser(this, username, password, nickname)
            if (success) {
                Toast.makeText(this, "회원가입이 완료되었습니다! 로그인 해주세요.", Toast.LENGTH_LONG).show()
                binding.etLoginUsername.setText(username)
                binding.etLoginPassword.setText(password)
                showView(binding.layoutLoginForm)
            } else {
                Toast.makeText(this, "이미 존재하는 아이디입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showView(viewToShow: View) {
        binding.layoutSelector.visibility = View.GONE
        binding.layoutLoginForm.visibility = View.GONE
        binding.layoutRegisterForm.visibility = View.GONE

        viewToShow.visibility = View.VISIBLE
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

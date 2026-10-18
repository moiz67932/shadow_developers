package com.example.nasconsports

import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

fun MainActivity.signInScreen() {
    start("Sign in")
    val view = layoutInflater.inflate(R.layout.screen_sign_in, content, true)
    val email = view.findViewById<EditText>(R.id.login_email)
    val password = view.findViewById<EditText>(R.id.login_password)
    val error = view.findViewById<TextView>(R.id.login_error)
    val demo = view.findViewById<android.widget.Spinner>(R.id.login_demo)
    val names = listOf("organizer", "manager", "admin", "player", "viewer")
    demo.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names.map { "${it.replaceFirstChar { c -> c.uppercase() }} · ${it}@nascon.test" })
    demo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, row: View?, position: Int, id: Long) {
            email.setText("${names[position]}@nascon.test"); password.setText("Nascon@26")
        }
    }
    val submit = view.findViewById<MaterialButton>(R.id.login_submit)
    submit.setOnClickListener {
        if (busy) return@setOnClickListener
        error.visibility = View.GONE
        val address = email.text.toString().trim()
        val secret = password.text.toString()
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() -> email.error = "Enter a valid email"
            secret.isEmpty() -> password.error = "Enter your password"
            forcedOffline() -> { error.text = "Use Open remembered account offline, or turn off Work offline in Settings."; error.visibility = View.VISIBLE }
            else -> {
                submit.isEnabled = false
                work({ ApiClient(api.base).call("/api/v1/login", JSONObject().put("email", address).put("password", secret)) }, {
                    password.text.clear(); acceptSession(it, address, secret)
                }, {
                    submit.isEnabled = true; error.text = if (it is ApiError) it.message else "Could not reach your server. Check Server connection settings."
                    error.visibility = View.VISIBLE
                })
            }
        }
    }
    password.setOnEditorActionListener { _, action, _ ->
        if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { submit.performClick(); true } else false
    }
    view.findViewById<MaterialButton>(R.id.login_offline).setOnClickListener {
        val address = email.text.toString().trim()
        val entry = remembered.read()?.optJSONObject(api.base + "|" + address)
        val cached = entry?.optJSONObject("session")
        if (cached == null || entry.optString("proof") != passwordProof(address, password.text.toString()) || !validRemembered(cached)) {
            error.text = "Sign in online first. Remembered accounts require the correct password and an unexpired session."
            error.visibility = View.VISIBLE
        } else {
            session = cached; sessions.save(cached); api.token = cached.getString("token")
            online = false; prefs.edit().putBoolean("offline", true).commit()
            cart.clear(); local.get("cart:$owner")?.let { saved -> saved.keys().forEach { cart[it] = saved.optInt(it) } }
            orderRequest = local.get("checkout:$owner")?.takeIf { it.has("order_id") }
            data = local.get("bootstrap:$owner") ?: JSONObject(); home()
        }
    }
    view.findViewById<MaterialButton>(R.id.login_register).setOnClickListener { signUpScreen() }
    view.findViewById<MaterialButton>(R.id.login_settings).setOnClickListener { settings() }
}

/** Same session vault, account/server cache namespace and credentials as existing sign-in. */
fun MainActivity.acceptSession(result: JSONObject, email: String, password: String) {
    result.getJSONObject("user").put("email", email)
    session = result
    api.token = result.getString("token")
    sessions.save(result)
    val vault = remembered.read() ?: JSONObject()
    vault.put(api.base + "|" + email, JSONObject().put("session", result).put("proof", passwordProof(email, password)))
    remembered.save(vault)
    cart.clear()
    local.get("cart:$owner")?.let { saved -> saved.keys().forEach { cart[it] = saved.optInt(it) } }
    orderRequest = local.get("checkout:$owner")?.takeIf { it.has("order_id") }
    history.clear()
    data = local.get("bootstrap:$owner") ?: JSONObject()
    refresh { home() }
}

fun MainActivity.signUpScreen() {
    start("Sign up")
    val view = layoutInflater.inflate(R.layout.screen_sign_up, content, true)
    val email = view.findViewById<EditText>(R.id.register_email)
    val password = view.findViewById<EditText>(R.id.register_password)
    val confirm = view.findViewById<EditText>(R.id.register_confirm)
    val error = view.findViewById<TextView>(R.id.register_error)
    val submit = view.findViewById<MaterialButton>(R.id.register_submit)
    view.findViewById<MaterialButton>(R.id.register_sign_in).setOnClickListener { login() }
    submit.setOnClickListener {
        if (busy) return@setOnClickListener
        error.visibility = View.GONE
        val address = email.text.toString().trim()
        val secret = password.text.toString()
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() -> email.error = "Enter a valid email address"
            secret.length !in 8..128 -> password.error = "Use 8–128 characters"
            secret != confirm.text.toString() -> confirm.error = "Passwords do not match"
            forcedOffline() -> { error.text = "Connect to your server and turn off Work offline to register."; error.visibility = View.VISIBLE }
            else -> {
                submit.isEnabled = false
                work({ ApiClient(api.base).call("/api/v1/register", JSONObject().put("email", address).put("password", secret)) }, { result ->
                    password.text.clear(); confirm.text.clear()
                    acceptSession(result, address, secret)
                }, { failure ->
                    submit.isEnabled = true
                    error.text = if (failure is ApiError) failure.message else "Could not reach your server. Your account has not been confirmed; retry or sign in."
                    error.visibility = View.VISIBLE
                })
            }
        }
    }
    confirm.setOnEditorActionListener { _, action, _ ->
        if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { submit.performClick(); true } else false
    }
}

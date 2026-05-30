package com.example.lifelink.ui.treasure;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lifelink.R;
import com.example.lifelink.data.treasure.WillSafeManager;
import com.google.android.material.button.MaterialButton;

public class WillSafeActivity extends AppCompatActivity {
    private WillSafeManager willSafeManager;

    private TextView tvStatus;
    private TextView tvHint;
    private LinearLayout layoutPasswordSetup;
    private LinearLayout layoutUnlock;
    private LinearLayout layoutEditor;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private EditText etUnlockPassword;
    private EditText etWillContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_will_safe);

        willSafeManager = new WillSafeManager(this);
        bindViews();
        bindActions();
        showInitialState();
    }

    private void bindViews() {
        findViewById(R.id.btn_will_safe_back).setOnClickListener(v -> finish());

        tvStatus = findViewById(R.id.tv_safe_status);
        tvHint = findViewById(R.id.tv_safe_hint);
        layoutPasswordSetup = findViewById(R.id.layout_password_setup);
        layoutUnlock = findViewById(R.id.layout_unlock);
        layoutEditor = findViewById(R.id.layout_will_editor);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        etUnlockPassword = findViewById(R.id.et_unlock_password);
        etWillContent = findViewById(R.id.et_will_content);
    }

    private void bindActions() {
        MaterialButton btnSetup = findViewById(R.id.btn_setup_password);
        MaterialButton btnUnlock = findViewById(R.id.btn_unlock_safe);
        MaterialButton btnSave = findViewById(R.id.btn_save_will);
        MaterialButton btnLock = findViewById(R.id.btn_lock_safe);

        btnSetup.setOnClickListener(v -> setupPassword());
        btnUnlock.setOnClickListener(v -> unlockSafe());
        btnSave.setOnClickListener(v -> saveWill());
        btnLock.setOnClickListener(v -> lockSafe());
    }

    private void showInitialState() {
        if (willSafeManager.hasPassword()) {
            lockSafe();
        } else {
            tvStatus.setText("Create Will Safe");
            tvHint.setText("Set a password first. Viewing or editing the will requires this password.");
            layoutPasswordSetup.setVisibility(View.VISIBLE);
            layoutUnlock.setVisibility(View.GONE);
            layoutEditor.setVisibility(View.GONE);
        }
    }

    private void setupPassword() {
        String password = etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (password.length() < 6) {
            etNewPassword.setError("At least 6 characters");
            return;
        }
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        try {
            willSafeManager.setPassword(password);
            etNewPassword.setText("");
            etConfirmPassword.setText("");
            Toast.makeText(this, "Safe created", Toast.LENGTH_SHORT).show();
            showEditor();
        } catch (Exception e) {
            showError("Create failed: ", e);
        }
    }

    private void unlockSafe() {
        try {
            if (willSafeManager.verifyPassword(etUnlockPassword.getText().toString())) {
                etUnlockPassword.setText("");
                showEditor();
            } else {
                etUnlockPassword.setError("Wrong password");
            }
        } catch (Exception e) {
            showError("Unlock failed: ", e);
        }
    }

    private void showEditor() {
        tvStatus.setText("Safe Unlocked");
        tvHint.setText("You can view or edit the will. The database stores encrypted content only.");
        layoutPasswordSetup.setVisibility(View.GONE);
        layoutUnlock.setVisibility(View.GONE);
        layoutEditor.setVisibility(View.VISIBLE);

        try {
            etWillContent.setText(willSafeManager.loadWill());
        } catch (Exception e) {
            showError("Read failed: ", e);
        }
    }

    private void saveWill() {
        String content = etWillContent.getText().toString().trim();
        if (content.isEmpty()) {
            etWillContent.setError("Please enter content first");
            return;
        }

        try {
            willSafeManager.saveWill(content);
            Toast.makeText(this, "Encrypted will saved to database", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError("Save failed: ", e);
        }
    }

    private void lockSafe() {
        tvStatus.setText("Safe Locked");
        tvHint.setText(willSafeManager.hasWill()
                ? "A will is saved. Enter the password to unlock."
                : "No will has been saved. Enter the password to start writing.");
        layoutPasswordSetup.setVisibility(View.GONE);
        layoutUnlock.setVisibility(View.VISIBLE);
        layoutEditor.setVisibility(View.GONE);
        etWillContent.setText("");
    }

    private void showError(String prefix, Exception e) {
        Toast.makeText(this, prefix + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}

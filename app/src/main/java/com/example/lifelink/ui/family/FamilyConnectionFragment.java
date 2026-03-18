package com.example.lifelink.ui.family;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.example.lifelink.data.family.FamilyDbHelper;
import com.example.lifelink.data.family.Guardian;
import com.example.lifelink.data.health.HealthData;
import com.example.lifelink.data.health.HealthDbHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FamilyConnectionFragment extends Fragment {

    private static final String TAG = "FamilyConnection";
    private MaterialButton btnCheckin;
    private SwitchMaterial switchAutoCheckin;
    
    private FamilyDbHelper familyDb;
    private HealthDbHelper healthDb;
    private MoneyPrinterApi qwenApi;
    private static final String QWEN_API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";

    private int debugClickCount = 0;
    
    private String pendingPhone;
    private String pendingMessage;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted && pendingPhone != null && pendingMessage != null) {
                    sendSmsRaw(pendingPhone, pendingMessage);
                    showSuccessDialog("守护者", pendingMessage);
                    pendingPhone = null;
                    pendingMessage = null;
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        familyDb = new FamilyDbHelper(requireContext());
        healthDb = new HealthDbHelper(requireContext());
        setupQwenApi();
        
        btnCheckin = view.findViewById(R.id.btn_checkin_today);
        switchAutoCheckin = view.findViewById(R.id.switch_auto_checkin);

        setupListeners(view);
    }

    private void setupQwenApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        qwenApi = retrofit.create(MoneyPrinterApi.class);
    }

    private void setupListeners(View rootView) {
        btnCheckin.setOnClickListener(v -> {
            familyDb.addCheckin(System.currentTimeMillis(), 1, "manual");
            Toast.makeText(getContext(), "✅ 打卡成功", Toast.LENGTH_SHORT).show();
        });

        View btnManage = rootView.findViewById(R.id.btn_manage_relatives);
        if (btnManage != null) {
            btnManage.setOnClickListener(v -> {
                debugClickCount++;
                if (debugClickCount >= 5) {
                    startEmergencyFlow("系统模拟测试：异常体征预警");
                    debugClickCount = 0;
                }
            });
        }
    }

    private void startEmergencyFlow(String reason) {
        List<Guardian> guardians = familyDb.getAllGuardians();
        if (guardians.isEmpty()) return;

        Guardian target = guardians.get(0);
        String promptText = "生成一段40字内的报警短信告知家属" + target.getName() + "老人出现" + reason + "情况。";

        // 将 String 包装成 List<Message> 以符合新的构造函数
        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatCompletionRequest.Message("user", promptText));

        qwenApi.chatCompletions(QWEN_API_KEY, new ChatCompletionRequest("qwen-plus", messages)).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        dispatchAlert(target, response.body().getFirstAnswer());
                    }
                });
            }
            @Override public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {}
        });
    }

    private void dispatchAlert(Guardian guardian, String content) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED) {
            sendSmsRaw(guardian.getPhone(), content);
            showSuccessDialog(guardian.getName(), content);
        } else {
            pendingPhone = guardian.getPhone();
            pendingMessage = content;
            requestPermissionLauncher.launch(Manifest.permission.SEND_SMS);
        }
    }

    private void sendSmsRaw(String phone, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = requireContext().getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            
            if (smsManager != null && message != null) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                Log.d(TAG, "Multipart SMS Sent to " + phone);
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS Failed", e);
        }
    }

    private void showSuccessDialog(String name, String content) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("🛡️ AI 守护执行成功")
                .setMessage("已向 " + name + " 发送短信：\n" + content)
                .setPositiveButton("确定", null)
                .show();
    }
}

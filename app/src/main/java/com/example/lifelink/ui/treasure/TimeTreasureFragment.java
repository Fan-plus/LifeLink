package com.example.lifelink.ui.treasure;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;

public class TimeTreasureFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_time_treasure, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 启动顶部光晕动画
        View halo = view.findViewById(R.id.view_halo);
        if (halo != null) {
            ObjectAnimator scaleAnim = ObjectAnimator.ofPropertyValuesHolder(
                    halo,
                    PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.4f),
                    PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.4f),
                    PropertyValuesHolder.ofFloat("alpha", 0.05f, 0.2f, 0.05f)
            );
            scaleAnim.setDuration(3000);
            scaleAnim.setRepeatCount(ValueAnimator.INFINITE);
            scaleAnim.setRepeatMode(ValueAnimator.REVERSE);
            scaleAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleAnim.start();
        }

        // 绑定点击事件：进入岁月文字列表
        view.findViewById(R.id.layout_text_memory).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MemoryListActivity.class);
            intent.putExtra("memory_type", "text");
            startActivity(intent);
        });

        // 绑定点击事件：进入留声往事列表
        view.findViewById(R.id.layout_audio_memory).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MemoryListActivity.class);
            intent.putExtra("memory_type", "audio");
            startActivity(intent);
        });

        // ⭐ 绑定点击事件：生成我的回忆录
        view.findViewById(R.id.btn_generate_memory_new).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MemoirDisplayActivity.class);
            startActivity(intent);
        });

        // 页面入口渐入动画
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(800).start();
    }
}

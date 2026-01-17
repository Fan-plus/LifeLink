package com.example.lifelink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        // 设置ViewPager适配器
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        // 添加Fragment并设置图标（使用Android内置图标）
        adapter.addFragment(new HomeFragment(), "首页", R.drawable.ic_home);
        adapter.addFragment(new MemoryGuardFragment(), "记忆守护", R.drawable.ic_memory);
        adapter.addFragment(new HealthMonitoringFragment(), "健康监测", R.drawable.ic_health);
        adapter.addFragment(new FamilyConnectionFragment(), "亲属互联", R.drawable.ic_family);
        adapter.addFragment(new WarmCompanionFragment(), "暖心陪伴", R.drawable.ic_companion);
        adapter.addFragment(new TimeTreasureFragment(), "时光珍藏", R.drawable.ic_treasure);

        viewPager.setAdapter(adapter);

        // 关联TabLayout和ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(adapter.getPageTitle(position));
            tab.setIcon(adapter.getPageIcon(position)); // 设置图标
        }).attach();
    }
}
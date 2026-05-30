package com.example.lifelink.ui.activity;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.example.lifelink.ui.common.ViewPagerAdapter;
import com.example.lifelink.ui.home.HomeFragment;
import com.example.lifelink.ui.memory.MemoryGuardFragment;
import com.example.lifelink.ui.health.HealthMonitoringFragment;
import com.example.lifelink.ui.family.FamilyConnectionFragment;
import com.example.lifelink.ui.warm.WarmCompanionFragment;
import com.example.lifelink.ui.treasure.TimeTreasureFragment;
import com.example.lifelink.R;

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

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        adapter.addFragment(new HomeFragment(), "首页", R.drawable.ic_home);
        adapter.addFragment(new MemoryGuardFragment(), "记忆守护", R.drawable.ic_memory);
        adapter.addFragment(new HealthMonitoringFragment(), "健康监测", R.drawable.ic_health);
        adapter.addFragment(new FamilyConnectionFragment(), "亲属互联", R.drawable.ic_family);
        adapter.addFragment(new WarmCompanionFragment(), "暖心陪伴", R.drawable.ic_companion);
        adapter.addFragment(new TimeTreasureFragment(), "时光珍藏", R.drawable.ic_treasure);

        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(adapter.getPageTitle(position));
            tab.setIcon(adapter.getPageIcon(position));
        }).attach();
    }

    public void switchToTab(int position) {
        if (viewPager != null && viewPager.getAdapter() != null
                && position >= 0 && position < viewPager.getAdapter().getItemCount()) {
            viewPager.setCurrentItem(position, true);
        }
    }
}

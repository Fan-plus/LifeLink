package com.example.lifelink.ui.common;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private final List<Fragment> fragmentList = new ArrayList<>();
    private final List<String> fragmentTitleList = new ArrayList<>();
    private final List<Integer> fragmentIconList = new ArrayList<>();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) { super(fragmentActivity); }

    public void addFragment(Fragment fragment, String title, int icon) { fragmentList.add(fragment); fragmentTitleList.add(title); fragmentIconList.add(icon); }

    @NonNull
    @Override
    public Fragment createFragment(int position) { return fragmentList.get(position); }

    @Override
    public int getItemCount() { return fragmentList.size(); }

    public String getPageTitle(int position) { return fragmentTitleList.get(position); }

    public int getPageIcon(int position) { return fragmentIconList.get(position); }
}

package com.gzb.coolweather.zcoolweather.activity;

import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.gzb.coolweather.R;
import com.gzb.coolweather.zcoolweather.bean.City;
import com.gzb.coolweather.zcoolweather.bean.County;
import com.gzb.coolweather.zcoolweather.bean.Province;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gzb on 18-5-2.
 */

public class ChooseAreaFragment extends Fragment {

    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        titleText = view.findViewById(R.id.title_text);
        listView = view.findViewById(R.id.list_view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            adapter = new ArrayAdapter<String>(getContext(),android.R.layout.simple_list_item_1,dataList);
        }
        listView.setAdapter(adapter);
        return view;

    }
}






























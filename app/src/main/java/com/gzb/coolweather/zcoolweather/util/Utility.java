package com.gzb.coolweather.zcoolweather.util;

import android.text.TextUtils;

import com.gzb.coolweather.zcoolweather.bean.City;
import com.gzb.coolweather.zcoolweather.bean.County;
import com.gzb.coolweather.zcoolweather.bean.Province;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by gzb on 18-5-2.
 */

public class Utility {

    /**
     * 解析省级数据
     * @param response
     * @return
     */
    public static boolean handlerProvinceResponse(String response){

        if(!TextUtils.isEmpty(response)){
            try{
                JSONArray allProvinces = new JSONArray(response);
                for(int i=0; i<allProvinces.length();i++){
                    JSONObject provinceObject = allProvinces.getJSONObject(i);
                    Province province = new Province();
                    province.setProvinceName(provinceObject.getString("name"));
                    province.setProvinceCode(provinceObject.getInt("id"));
                }
                return true;

            }catch (JSONException e){
                e.printStackTrace();
            }
        }

        return false;
    }

    public static boolean handlerCityResponse(String response, int provinceId){

        if(!TextUtils.isEmpty(response)){
            try{
                JSONArray allCities = new JSONArray(response);
                for(int i=0; i<allCities.length();i++){
                    JSONObject cityObject = allCities.getJSONObject(i);
                    City city = new City();
                    city.setCityName(cityObject.getString("name"));
                    city.setCityCode(cityObject.getInt("id"));
                    city.setProvinceId(provinceId);
                    city.save();
                }
                return true;

            }catch (JSONException e){
                e.printStackTrace();
            }
        }

        return false;
    }

    public static boolean handlerCountyResponse(String response, int cityId){

        if(!TextUtils.isEmpty(response)){
            try{
                JSONArray allCounties = new JSONArray(response);
                for(int i=0; i<allCounties.length();i++){
                    JSONObject countyObject = allCounties.getJSONObject(i);
                    County county = new County();
                    county.setCountyName(countyObject.getString("name"));
                    county.setWeatherId(countyObject.getString("weather"));
                    county.setCityId(cityId);
                    county.save();
                }
                return true;

            }catch (JSONException e){
                e.printStackTrace();
            }
        }

        return false;
    }

}





































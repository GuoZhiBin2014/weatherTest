package com.gzb.coolweather.activity;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.gzb.coolweather.R;
import com.gzb.coolweather.bean.ClassificationImage;
import com.gzb.coolweather.db.ClassificationDB;
import com.gzb.coolweather.utils.Config;
import com.gzb.coolweather.utils.ConvertUtil;
import com.gzb.coolweather.utils.DialogUtil;
import com.gzb.coolweather.utils.FileUtils;
import com.gzb.coolweather.utils.RootUtil;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by dell on 18-4-10.
 */

public class OffLineClassifiactionAct extends BaseActivity {
    private final String TAG="OffLineClassifiaction";
    private int sumTime = 0;
    private ClassificationDB offlineDB;
    private static boolean isRooted=false;
    private final int SHOW_OFFLINE_DATA = 0x01;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case SHOW_OFFLINE_DATA:
                    try {
                        offLineProcess();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        setListener();
    }

    private void init(){
        offlineDB=new ClassificationDB(getApplicationContext());
        offlineDB.open();
        if(RootUtil.getRoot(getPackageCodePath())){
            isRooted=true;
        }else{
            isRooted=false;
        }
    }

    private void setListener(){
        basebtn_begin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!Config.getIsCPUMode(getApplicationContext())){
                    if(isRooted){
                        basebtn_begin.setVisibility(View.GONE);
                        basebtn_end.setVisibility(View.VISIBLE);
                        runOffline();
                        basebtn_end.setText("检测中...");
                        basebtn_end.setClickable(false);
                    }else{
                        Toast.makeText(getApplicationContext(), "Get Root first.", Toast.LENGTH_SHORT).show();
                    }
                }else{
                    DialogUtil.showDialog(OffLineClassifiactionAct.this,"操作提醒","需要在主页面打开IPU模式","确定");
                }
            }
        });
        basebtn_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(OffLineClassifiactionAct.this,"test end",Toast.LENGTH_SHORT).show();
                basebtn_end.setVisibility(View.GONE);
                basebtn_begin.setVisibility(View.VISIBLE);
            }
        });
    }

    public void runOffline(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cmd = "su -s sh  -c /data/test/offline/classification/offline_classify.sh";
                    Log.e("huangyaling", "cmd");
                    Process proc = Runtime.getRuntime().exec(cmd);
                    //proc.waitFor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Message message = new Message();
                message.what = SHOW_OFFLINE_DATA;
                handler.sendMessage(message);
            }
        }).start();
    }

    private void offLineProcess() throws IOException {
        Log.i(TAG, "IPUProcess: ");
        //数据信息展示
        function_text.setVisibility(View.GONE);
        testPro.setText("图片分类数据显示...");
        testResult.setVisibility(View.VISIBLE);
        testTime.setVisibility(View.VISIBLE);
        textFps.setVisibility(View.VISIBLE);
        ipu_text_pro.setVisibility(View.VISIBLE);
        ipu_progress.setVisibility(View.VISIBLE);
        try {
            final ArrayList<ClassificationImage> classificationOfflineImages = FileUtils.readClassificationIPUTxt(Config.offLine_ipu_path);
            int i = 0;
            for (final ClassificationImage image : classificationOfflineImages) {
                final String time = image.getTime();
                final double delay = Integer.valueOf(time)/1000.0;
                final String result = image.getResult();
                final double fps = ConvertUtil.getFps(String.valueOf(1000/delay));
                sumTime += delay;
                final int finalI = i;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(ipu_progress!=null){
                            ipu_progress.setVisibility(View.GONE);
                            ipu_text_pro.setVisibility(View.GONE);
                        }
                        base_img.setImageBitmap(BitmapFactory.decodeFile(image.getName()));
                        testResult.setText(getResources().getString(R.string.test_result) + result);
                        testTime.setText(getResources().getString(R.string.test_time) + time + "μs");
                        textFps.setText(getResources().getString(R.string.test_fps) + fps + getString(R.string.test_fps_units));
                        if(finalI >0){
                            offlineDB.addOfflineClassification(image.getName(), time, String.valueOf(fps), image.getResult());
                        }
                    }
                },sumTime);


                if (i == classificationOfflineImages.size() - 1) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "检测结束", Toast.LENGTH_SHORT).show();
                            testPro.setText(getString(R.string.detection_end_guide));
                            basebtn_end.setText("停止测试");
                            basebtn_begin.setVisibility(View.VISIBLE);
                            basebtn_end.setVisibility(View.GONE);
                        }
                    }, sumTime);
                }
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

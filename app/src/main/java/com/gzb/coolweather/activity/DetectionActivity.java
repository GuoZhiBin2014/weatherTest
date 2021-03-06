package com.gzb.coolweather.activity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gzb.coolweather.R;
import com.gzb.coolweather.bean.DetectionImage;
import com.gzb.coolweather.caffenative.CaffeDetection;
import com.gzb.coolweather.db.DetectionDB;
import com.gzb.coolweather.task.CNNListener;
import com.gzb.coolweather.utils.Config;
import com.gzb.coolweather.utils.ConvertUtil;
import com.gzb.coolweather.utils.FileUtils;
import com.gzb.coolweather.utils.RootUtil;
import com.gzb.coolweather.utils.StatusBarCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DetectionActivity extends AppCompatActivity implements View.OnClickListener, CNNListener {

    private final String TAG = "DetectionActivity";

    private Toolbar toolbar;
    private TextView testNet;
    private TextView loadCaffe;
    private TextView testTime;
    private TextView function_text;
    private TextView textFps;
    private TextView testPro;
    private Button detection_begin;
    private Button detection_end;
    private ImageView ivCaptured;
    private ProgressBar ipuProgress;
    private TextView ipu_pro_text;
    private boolean isExist = true;
    private boolean isRooted = false;
    public Thread testThread;
    private Bitmap resBitmap;
    private File imageFile;
    private Bitmap bitmap;
    public static int index = 0;
    private CaffeDetection caffeDetection;
    private double detectionTime;
    private DetectionDB detectionDB;
    private long loadDTime;
    private final int START_LOAD_DETECT = 2;
    private final int LOED_DETECT_END = 3;
    private final int IPU_DETECT_END = 4;
    private final int UPDATE_IMG = 5;
    private ArrayList<DetectionImage> mDetectionImageArrayList;
    private Bundle mBundle;

    private static final String BITMAP = "Bitmap";
    private static final String TIME = "Time";
    private static final String FPS = "Fps";
    private static final String NETTYPE = "NetType";

    private static final String FASTRCNN = "Fast-RCNN";
    private static final String RESNET50 = "ResNet50";
    private int[] mPostTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarCompat.compat(this, ContextCompat.getColor(this, R.color.colorPrimary));
        setContentView(R.layout.classification_layout);
        init();
        setActionBar();
        if (!Config.getIsCPUMode(DetectionActivity.this)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (RootUtil.getRoot(getPackageCodePath())) {
                        isRooted = true;
                    } else {
                        isRooted = false;
                    }
                }
            }).start();
        }
    }

    private void init() {
        toolbar = findViewById(R.id.classification_toolbar);
        ivCaptured = findViewById(R.id.classification_img);
        testNet = findViewById(R.id.test_result);
        testTime = findViewById(R.id.test_time);
        loadCaffe = findViewById(R.id.load_caffe);
        function_text = findViewById(R.id.function_describe);
        textFps = findViewById(R.id.test_fps);
        testPro = findViewById(R.id.test_guide);
        detection_begin = findViewById(R.id.classification_begin);
        detection_end = findViewById(R.id.classification_end);
        ipuProgress = findViewById(R.id.ipu_progress);
        ipu_pro_text = findViewById(R.id.ipu_pro_text);

        loadCaffe.setText("");
        testNet.setText(getString(R.string.decete_type) + String.valueOf(getIntent().getSerializableExtra("netType")));
        testPro.setText(R.string.detection_result);
        function_text.setText(R.string.detection_introduce);
        testNet.setText(R.string.decete_type);

        detection_begin.setOnClickListener(this);
        detection_end.setOnClickListener(this);

        detectionDB = new DetectionDB(getApplicationContext());
        detectionDB.open();

        caffeDetection = new CaffeDetection();
    }

    /**
     * 设置ActionBar
     */
    private void setActionBar() {
        String mode = Config.getIsCPUMode(DetectionActivity.this) ? getString(R.string.cpu_mode) : getString(R.string.ipu_mode);
        toolbar.setTitle(getString(R.string.detection_title) + "--" + mode);
        Drawable toolDrawable = ContextCompat.getDrawable(getApplicationContext(), R.drawable.toolbar_bg);
        toolDrawable.setAlpha(50);
        toolbar.setBackground(toolDrawable);
        setSupportActionBar(toolbar);
        /*显示Home图标*/
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.classification_begin:
                if (Config.getIsCPUMode(DetectionActivity.this)) {
                    CPUDetect();
                } else {
                    IPUDetect();
                }
                break;
            case R.id.classification_end:
                testPro.setText(getString(R.string.detection_pasue_guide));
                isExist = false;
                detection_begin.setVisibility(View.VISIBLE);
                detection_end.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    private void CPUDetect() {
        Log.i("DetectionActivity", "CPU Detect");
        function_text.setVisibility(View.GONE);
        testPro.setText(getString(R.string.detection_begin_guide));
        testTime.setVisibility(View.VISIBLE);
        textFps.setVisibility(View.VISIBLE);
        testNet.setVisibility(View.VISIBLE);
        index = 0;
        Config.isFastRCNN = false;
        isExist = true;
        startDetect();
        detection_begin.setVisibility(View.GONE);
        detection_end.setVisibility(View.VISIBLE);
    }

    private void IPUDetect() {
        if (isRooted) {
            function_text.setVisibility(View.GONE);
            isExist = true;
            ipuProgress.setVisibility(View.VISIBLE);
            ipu_pro_text.setVisibility(View.VISIBLE);
            startDetect();
            detection_begin.setVisibility(View.GONE);
            detection_end.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(getApplicationContext(), "Get Root first.", Toast.LENGTH_SHORT).show();
        }
    }

    public void loadModel() {
        Message msg = new Message();
        msg.what = START_LOAD_DETECT;
        handler.sendMessage(msg);

        long startTime = SystemClock.uptimeMillis();
        caffeDetection.setNumThreads(4);
        Log.e(TAG, "loadModel: " + Config.getIsCPUMode(DetectionActivity.this));
        caffeDetection.loadModel(Config.dModelProto, Config.dModelBinary, Config.getIsCPUMode(DetectionActivity.this));
        caffeDetection.setMean(Config.dModelMean);

        loadDTime = SystemClock.uptimeMillis() - startTime;

        Config.isFastRCNN = false;
        Config.isResNet50 = true;

        if (loadDTime > 100) {
            Config.loadDetecteTime = loadDTime;
        }

        Message msg_end = new Message();
        msg_end.what = LOED_DETECT_END;
        handler.sendMessage(msg_end);
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_LOAD_DETECT:
                    loadCaffe.setText(R.string.load_data_detection);
                    testNet.setText(getString(R.string.decete_type) + RESNET50);
                    break;
                case LOED_DETECT_END:
                    loadCaffe.setText(getResources().getString(R.string.detection_load_model) + Config.loadDetecteTime + "ms");
                    break;
                case IPU_DETECT_END:
                    Toast.makeText(getApplicationContext(), "检测结束", Toast.LENGTH_SHORT).show();
                    testPro.setText(getString(R.string.detection_end_guide));
                    isExist = false;
                    detection_begin.setVisibility(View.VISIBLE);
                    detection_end.setVisibility(View.GONE);
                    break;
                case UPDATE_IMG:
                    String netType = msg.getData().getString(NETTYPE);
                    detection_end.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(getApplicationContext(), "正在检测中，不能停止", Toast.LENGTH_SHORT).show();
                        }
                    });
                    if (ipuProgress != null) {
                        ipuProgress.setVisibility(View.GONE);
                        ipu_pro_text.setVisibility(View.GONE);
                    }
                    int time = msg.getData().getInt(TIME);
                    Double fps = msg.getData().getDouble(FPS);
                    Bitmap bitmapToShow = msg.getData().getParcelable(BITMAP);
                    ivCaptured.setImageBitmap(bitmapToShow);
                    testNet.setText(getString(R.string.decete_type) + netType);
                    testTime.setText(getResources().getString(R.string.test_time) + time + "ms");
                    textFps.setText(getResources().getString(R.string.test_fps) + fps + getResources().getString(R.string.test_fps_units));
                    break;
                default:
                    break;
            }
        }
    };

    private void startDetect() {
        testThread = new Thread(new Runnable() {
            @Override
            public synchronized void run() {
                if (Config.getIsCPUMode(DetectionActivity.this)) {
                    loadModel();
                    executeImg();
                } else {
                    CNNTask cnnTask = new CNNTask(DetectionActivity.this);
                    cnnTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }
        });
        if (isExist) {
            testThread.start();
        }
    }

    public void executeImg() {
        imageFile = new File(Config.imagePath, Config.dImageArray[index]);
        bitmap = BitmapFactory.decodeFile(imageFile.getPath());
        CNNTask cnnTask = new CNNTask(DetectionActivity.this);
        if (imageFile.exists()) {
            cnnTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            Log.e(TAG, "file is not exist");
        }
    }

    private class CNNTask extends AsyncTask<Void, Void, Void> {
        private CNNListener listener;
        private long startTime;

        public CNNTask(CNNListener listener) {
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (!Config.getIsCPUMode(DetectionActivity.this)) {
                startTime = SystemClock.uptimeMillis();
                try {
                    String cmd = "su -s sh  -c /data/test/caffe_ipu/detection.sh";
                    Process proc = Runtime.getRuntime().exec(cmd);
                    Thread.sleep(1000);
                    //proc.waitFor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
            startTime = SystemClock.uptimeMillis();
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, w, h);
            int[] resultInt = caffeDetection.grayPoc(pixels, w, h);
            resBitmap = Bitmap.createBitmap(resultInt, w, h, bitmap.getConfig());
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            detectionTime = SystemClock.uptimeMillis() - startTime;
            listener.onTaskCompleted(0);
            super.onPostExecute(aVoid);
        }
    }

    private String getFps(double classificationTime) {
        double fps = 60 * 1000 / classificationTime;
        return String.valueOf(fps);
    }

    @Override
    public void onTaskCompleted(int result) {
        if (isExist) {
            if (Config.getIsCPUMode(DetectionActivity.this)) {
                CPUProcess();
            } else {
                IPUProcess();
            }
        } else {
            testPro.setText(getString(R.string.detection_end_guide));
        }
    }


    private synchronized void IPUProcess() {
        String IPUTxtPath = Config.detect_ipu_path;
        testTime.setVisibility(View.VISIBLE);
        textFps.setVisibility(View.VISIBLE);
        testNet.setVisibility(View.VISIBLE);
        testPro.setText("ipu模式检测....");
        mPostTime = new int[]{0};
        ivCaptured.setScaleType(ImageView.ScaleType.FIT_XY);
        try {
            File file = new File(IPUTxtPath);
            if (file.exists()) {
                mDetectionImageArrayList = FileUtils.readDetectionIPUTxt(IPUTxtPath);
                for (index = 0; index < mDetectionImageArrayList.size(); index++) {
                    String netType = mDetectionImageArrayList.get(index).getNetType();
                    String picName = mDetectionImageArrayList.get(index).getName();
                    int time = Integer.parseInt(mDetectionImageArrayList.get(index).getTime());
                    mPostTime[0] = mPostTime[0] + time;
                    double fps = ConvertUtil.getFps(mDetectionImageArrayList.get(index).getFps());
                    final Bitmap bitmap = BitmapFactory.decodeFile(Config.caffe_result + picName);
                    if (index > 0) {
                        detectionDB.addIPUClassification(picName, String.valueOf(time), String.valueOf(fps), netType);
                        storeIPUImage(bitmap);
                    }
                    mBundle = new Bundle();
                    mBundle.putParcelable(BITMAP, bitmap);
                    mBundle.putInt(TIME, time);
                    mBundle.putDouble(FPS, fps);
                    mBundle.putString(NETTYPE, netType);
                    Message msg_update_img = new Message();
                    msg_update_img.what = UPDATE_IMG;
                    msg_update_img.setData(mBundle);
                    handler.sendMessageDelayed(msg_update_img, mPostTime[0]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Message msg_IPU_end = new Message();
        msg_IPU_end.what = IPU_DETECT_END;
        handler.sendMessageDelayed(msg_IPU_end, mPostTime[0]);
    }

    private void CPUProcess() {
        ivCaptured.setScaleType(ImageView.ScaleType.FIT_XY);
        ivCaptured.setImageBitmap(resBitmap);
        String netType;
        if (index > (Config.dImageArray.length / 2) - 1) {
            netType = FASTRCNN;
        } else {
            netType = RESNET50;
        }

        if (index > 0) {
            detectionDB.addDetection(Config.dImageArray[index], String.valueOf((int) detectionTime), getFps(detectionTime), netType);
            storeImage(resBitmap);
        }

        testTime.setText(getResources().getString(R.string.test_time) + String.valueOf(detectionTime) + "ms");
        textFps.setText(getResources().getString(R.string.test_fps) + ConvertUtil.getFps(getFps(detectionTime)) + getResources().getString(R.string.test_fps_units));

        if ((index > (Config.dImageArray.length / 2) - 1) && !Config.isFastRCNN) {
            loadFastRCNN();
        }
        if (index < Config.dImageArray.length - 1) {
            executeImg();
        } else {
            Toast.makeText(this, "检测结束", Toast.LENGTH_SHORT).show();
            testPro.setText(getString(R.string.detection_end_guide));
            isExist = false;
            detection_begin.setVisibility(View.VISIBLE);
            detection_end.setVisibility(View.GONE);
        }
        index++;
    }

    protected void loadFastRCNN() {
        long startTime = SystemClock.uptimeMillis();
        caffeDetection.setNumThreads(4);
        caffeDetection.loadModel(Config.dModelProto_FRC, Config.dModelBinary_FRC, Config.getIsCPUMode(DetectionActivity.this));
        caffeDetection.setMean(Config.dModelMean_FRC);

        Config.isResNet50 = false;
        Config.isFastRCNN = true;
        loadDTime = SystemClock.uptimeMillis() - startTime;
        loadCaffe.setText(getResources().getString(R.string.detection_change_model) + loadDTime + "ms");
        testNet.setText(getString(R.string.decete_type) + FASTRCNN);
    }

    protected void loadResNet() {
        long startTime = SystemClock.uptimeMillis();
        caffeDetection.setNumThreads(4);
        caffeDetection.loadModel(Config.dModelProto_101, Config.dModelBinary_101, Config.getIsCPUMode(DetectionActivity.this));
        caffeDetection.setMean(Config.dModelMean_101);

        Config.isResNet50 = false;
        Config.isResNet101 = true;
        loadDTime = SystemClock.uptimeMillis() - startTime;
        loadCaffe.setText(getResources().getString(R.string.detection_change_model) + loadDTime + "ms");
        testNet.setText(getString(R.string.decete_type) + "ResNet101");
    }

    public void storeImage(Bitmap bitmap) {
        File file = new File(Config.dImagePath, "detec-" + index + ".jpg");
        if (!file.exists()) {
            file.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeIPUImage(Bitmap bitmap) {
        File file = new File(Config.dImagePath, "detecIPU-" + index + ".jpg");
        if (!file.exists()) {
            file.mkdirs();
        }
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        index = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isExist = false;
    }
}
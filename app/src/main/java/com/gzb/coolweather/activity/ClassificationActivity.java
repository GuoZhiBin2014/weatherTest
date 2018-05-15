package com.gzb.coolweather.activity;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gzb.coolweather.R;
import com.gzb.coolweather.bean.ClassificationImage;
import com.gzb.coolweather.caffenative.CaffeMobile;
import com.gzb.coolweather.db.ClassificationDB;
import com.gzb.coolweather.task.CNNListener;
import com.gzb.coolweather.utils.Config;
import com.gzb.coolweather.utils.ConvertUtil;
import com.gzb.coolweather.utils.DialogUtil;
import com.gzb.coolweather.utils.FileUtils;
import com.gzb.coolweather.utils.RootUtil;
import com.gzb.coolweather.utils.StatusBarCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;

public class ClassificationActivity extends AppCompatActivity implements CNNListener {
    private static final String LOG_TAG = "ClassificationActivity";
    private final int START_LOADMODEL = 1;
    private final int END_LODEMODEL = 2;
    private final int CHANGE_IMAGE = 3;
    private final int SHOW_IPU_DATA = 4;

    /**
     * 相关组件
     */
    private android.support.v7.widget.Toolbar toolbar;
    private Button classification_begin;
    private Button classification_end;
    private ImageView ivCaptured;
    private TextView textFps;
    private TextView testResult;
    private TextView loadCaffe;
    private TextView testTime;
    private TextView function_text;
    private TextView testPro;
    private CaffeMobile caffeMobile;
    private ProgressBar ipu_progress;
    private TextView ipu_text_pro;

    /**
     * 相关变量
     */
    private Bitmap bmp;
    private long end_time;
    public Thread testThread;
    public static int startIndex = 0;
    public static boolean isExist = true;
    private double classificationTime;
    private static String[] IMAGENET_CLASSES;
    private ClassificationDB classificationDB;
    private static float TARGET_WIDTH;
    private static float TARGET_HEIGHT;
    private File imageFile = new File(Config.sdcard, Config.imageName[0]);
    Timer timer = new Timer();
    private int sumTime = 0;
    private boolean isRooted = false;

    private ArrayList<ClassificationImage> arrayList = new ArrayList<>();

    private Boolean deploy_prototxt = true;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_LOADMODEL:
                    loadCaffe.setText("开始加载分类网络...");
                    break;
                case END_LODEMODEL:
                    loadCaffe.setText(getResources().getString(R.string.load_model) + Config.loadClassifyTime + "ms");
                    if (!deploy_prototxt) {
                        loadCaffe.setText("单层网络模型,效率高");
                    }
                    break;
                case CHANGE_IMAGE:
                    changeImage();
                    break;
                case SHOW_IPU_DATA:
                    try {
                        IPUProcess();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 加载模型
     */
    static {
        System.loadLibrary("caffe_jni");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarCompat.compat(this, ContextCompat.getColor(this, R.color.colorPrimary));
        setContentView(R.layout.classification_layout);
        init();

        classification_begin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (Config.getIsCPUMode(ClassificationActivity.this)) {
                    function_text.setVisibility(View.GONE);
                    testPro.setText(getString(R.string.classification_begin_guide));
                    testResult.setVisibility(View.VISIBLE);
                    testTime.setVisibility(View.VISIBLE);
                    textFps.setVisibility(View.VISIBLE);
                    startIndex = 0;
                    isExist = true;
                    startThread();
                    classification_begin.setVisibility(View.GONE);
                    classification_end.setVisibility(View.VISIBLE);

                } else {
                    if (!deploy_prototxt) {
                        DialogUtil.showDialog(ClassificationActivity.this, "操作提醒", "当前处于IPU模式，本功能需要CPU模式下运行，请返回至主页面打开CPU模式", "确定");
                    } else {
                       if (isRooted) {
                            testResult.setVisibility(View.GONE);
                            testResult.setText("分类结果：");
                            testTime.setVisibility(View.GONE);
                            testTime.setText("测试时间：");
                            textFps.setVisibility(View.GONE);
                            textFps.setText("Fps值：");
                            function_text.setVisibility(View.GONE);
                            testPro.setText("初始化ipu....");
                            runIpu();
                            classification_end.setText("检测中...");
                            classification_end.setClickable(false);
                            classification_begin.setVisibility(View.GONE);
                            classification_end.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(getApplicationContext(), "Get Root first.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });
        classification_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testPro.setText(getString(R.string.classification_pasue_guide));
                isExist = false;
                classification_begin.setVisibility(View.VISIBLE);
                classification_end.setVisibility(View.GONE);
                if (!Config.getIsCPUMode(ClassificationActivity.this)) {

                }
                arrayList.clear();
                startIndex = 0;
                testResult.setVisibility(View.GONE);
                testTime.setVisibility(View.GONE);
                textFps.setVisibility(View.GONE);
                function_text.setVisibility(View.VISIBLE);
            }
        });

        //读取类别文件
        AssetManager am = this.getAssets();
        try {
            InputStream is = am.open("file/synset_words.txt");
            Scanner sc = new Scanner(is);
            List<String> lines = new ArrayList<String>();
            while (sc.hasNextLine()) {
                final String temp = sc.nextLine();
                lines.add(temp.substring(temp.indexOf(" ") + 1));
            }
            IMAGENET_CLASSES = lines.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!Config.getIsCPUMode(ClassificationActivity.this)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (RootUtil.getRoot(getPackageCodePath())) {
                        isRooted = true;
                    } else {
                        isRooted = false;
                    }
                    RootUtil.getRoot(getPackageCodePath());
                }
            }).start();
        }

        /**
         * 获取网络类型
         */
        Intent intent = getIntent();
        deploy_prototxt = intent.getBooleanExtra("deploy_prototxt", true);
        if (!deploy_prototxt) {
            loadCaffe.setText("单层网络模型,效率高");
            function_text.setText("图片分类:\n\t\t\t\t加载图片分类单层网络模型,相比多层网络,正确率较低,效率高.");
        }

        setActionBar();
    }

    public void changeImage() {
        ClassificationImage image = arrayList.get(startIndex);
        File img = new File(Config.imagePath, image.getName());
        ivCaptured.setImageBitmap(BitmapFactory.decodeFile(img.getPath()));
        testResult.setText(getResources().getString(R.string.test_result) + image.getResult());
        testTime.setText(getResources().getString(R.string.test_time) + image.getTime() + "ms");
        textFps.setText(getResources().getString(R.string.test_fps) + ConvertUtil.getFps(image.getFps()) + getResources().getString(R.string.test_fps_units));
        classificationDB.addIPUClassification(image.getName(), image.getTime(), image.getFps(), image.getResult());
        startIndex++;
        testPro.setText("图片分类进行中...(" + startIndex + "%)");

        if (startIndex >= arrayList.size()) {
            timer.cancel();
            isExist = false;
            testPro.setText(getString(R.string.classification_end_guide));
            Toast.makeText(ClassificationActivity.this, "检测结束", Toast.LENGTH_SHORT).show();
            classification_begin.setVisibility(View.VISIBLE);
            classification_end.setVisibility(View.GONE);
            startIndex = 0;
            arrayList.clear();
        }
    }


    public void runIpu() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cmd = "su -s sh  -c /data/test/caffe_ipu/classification.sh";
                    Log.e("huangyaling", "cmd");
                    Process proc = Runtime.getRuntime().exec(cmd);
                    //proc.waitFor();
                    //                    Thread.sleep(5000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Message message = new Message();
                message.what = SHOW_IPU_DATA;
                handler.sendMessage(message);

            }
        }).start();

    }

    /**
     * 初始化组件
     */
    private void init() {
        ivCaptured = findViewById(R.id.classification_img);
        testResult = findViewById(R.id.test_result);
        testTime = findViewById(R.id.test_time);
        loadCaffe = findViewById(R.id.load_caffe);
        function_text = findViewById(R.id.function_describe);
        textFps = findViewById(R.id.test_fps);
        testPro = findViewById(R.id.test_guide);
        classification_begin = findViewById(R.id.classification_begin);
        classification_end = findViewById(R.id.classification_end);
        toolbar = findViewById(R.id.classification_toolbar);
        loadCaffe.setText("");
        classificationDB = new ClassificationDB(getApplicationContext());
        classificationDB.open();
        ipu_progress = findViewById(R.id.ipu_progress);
        ipu_text_pro = findViewById(R.id.ipu_pro_text);
    }

    /**
     * 开始检测
     */
    public void startThread() {
        testThread = new Thread(new Runnable() {
            @Override
            public synchronized void run() {
                if (Config.getIsCPUMode(ClassificationActivity.this)) {
                    load();
                    executeImg();
                }
            }
        });
        if (isExist) {
            testThread.start();
        }
    }

    /**
     * 加载模型
     */
    private void load() {
        Message msg = new Message();
        msg.what = START_LOADMODEL;
        handler.sendMessage(msg);

        //加载模型
        caffeMobile = new CaffeMobile();
        caffeMobile.setNumThreads(4);
        long start_time = SystemClock.uptimeMillis();

        /**
         * 加载不同类型的网网络（多层网络/单层网络）
         */
        if (deploy_prototxt) {
            caffeMobile.loadModel(Config.modelProto, Config.modelBinary, Config.getIsCPUMode(ClassificationActivity.this));
        } else {
            caffeMobile.loadModel(Config.simple_modelProto, Config.simple_modelBinary, Config.getIsCPUMode(ClassificationActivity.this));
        }

        end_time = SystemClock.uptimeMillis() - start_time;
        float[] meanValues = {104, 117, 123};
        caffeMobile.setMean(meanValues);

        if (end_time > 100) {
            Config.loadClassifyTime = end_time;
        }

        Message msg_end = new Message();
        msg_end.what = END_LODEMODEL;
        handler.sendMessage(msg_end);
    }

    /**
     * 传入检测图片
     */
    private void executeImg() {
        imageFile = new File(Config.imagePath, Config.imageName[startIndex]);
        bmp = BitmapFactory.decodeFile(imageFile.getPath());
        CNNTask cnnTask = new CNNTask(ClassificationActivity.this);
        if (imageFile.exists()) {
            cnnTask.execute(imageFile.getPath());
        } else {
            Log.d(LOG_TAG, "file is not exist");
        }
    }

    /**
     * 设置ActionBar
     */
    private void setActionBar() {
        String mode = Config.getIsCPUMode(ClassificationActivity.this) ? getString(R.string.cpu_mode) : getString(R.string.ipu_mode);
        if(!deploy_prototxt){
            toolbar.setTitle("CPU单层图片分类");
        }else{
            toolbar.setTitle(getString(R.string.gv_text_item1) + "--" + mode);
        }
        setSupportActionBar(toolbar);
        Drawable toolDrawable = ContextCompat.getDrawable(getApplicationContext(), R.drawable.toolbar_bg);
        toolDrawable.setAlpha(50);
        toolbar.setBackground(toolDrawable);
        /*显示Home图标*/
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }


    public static Bitmap zoomBitmap(Bitmap target) {
        int width = target.getWidth();
        int height = target.getHeight();
        Matrix matrix = new Matrix();
        float scaleWidth = ((float) TARGET_WIDTH) / width;
        float scaleHeight = ((float) TARGET_HEIGHT) / height;
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap result = Bitmap.createBitmap(target, 0, 0, width,
                height, matrix, true);
        return result;
    }

    /**
     * FPS格式转换
     *
     * @param classificationTime
     * @return
     */
    private String getFps(double classificationTime) {
        double fps = 60 * 1000 / classificationTime;
        Log.d(LOG_TAG, "fps:" + fps);
        return String.valueOf(fps);
    }

    private class CNNTask extends AsyncTask<String, Void, Integer> {

        private CNNListener listener;
        private long startTime;

        public CNNTask(CNNListener listener) {
            this.listener = listener;
        }

        @Override
        protected Integer doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            return caffeMobile.predictImage(strings[0])[0];
        }

        @Override
        protected void onPostExecute(Integer integer) {
            classificationTime = SystemClock.uptimeMillis() - startTime;
            listener.onTaskCompleted(integer);
            super.onPostExecute(integer);
        }
    }

    @Override
    public void onTaskCompleted(int result) {
        if (isExist) {
            CPUProcess(result);
        } else {
            testPro.setText(getString(R.string.classification_end_guide));
        }
    }

    private void IPUProcess() throws IOException {
        //数据信息展示
        function_text.setVisibility(View.GONE);
        testPro.setText("图片分类数据显示...");
        testResult.setVisibility(View.VISIBLE);
        testTime.setVisibility(View.VISIBLE);
        textFps.setVisibility(View.VISIBLE);
        ipu_text_pro.setVisibility(View.VISIBLE);
        ipu_progress.setVisibility(View.VISIBLE);
        //modify for ipu
        try {
            final ArrayList<ClassificationImage> classificationIPUImages = FileUtils.readClassificationIPUTxt(Config.classify_ipu_path);
            int i = 0;
            for (final ClassificationImage image : classificationIPUImages) {
                final String time = image.getTime();
                final int delay = Integer.valueOf(time);
                final String result = image.getResult();
                final int fps = ConvertUtil.getFps(image.getFps());
                sumTime += delay;
                final int finalI = i;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (ipu_progress != null) {
                            ipu_progress.setVisibility(View.GONE);
                            ipu_text_pro.setVisibility(View.GONE);
                        }
                        ivCaptured.setImageBitmap(BitmapFactory.decodeFile(image.getName()));
                        testResult.setText(getResources().getString(R.string.test_result) + result);
                        testTime.setText(getResources().getString(R.string.test_time) + time + "ms");
                        textFps.setText(getResources().getString(R.string.test_fps) + fps + getResources().getString(R.string.test_fps_units));
                        if (finalI > 0) {
                            classificationDB.addIPUClassification(image.getName(), image.getTime(), image.getFps(), image.getResult());
                        }
                    }
                }, sumTime);

                if (i == classificationIPUImages.size() - 1) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "检测结束", Toast.LENGTH_SHORT).show();
                            testPro.setText(getString(R.string.detection_end_guide));
                            classification_end.setText("停止测试");
                            classification_begin.setVisibility(View.VISIBLE);
                            classification_end.setVisibility(View.GONE);
                        }
                    }, sumTime);
                }
                i++;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //modify for ipu
    }


    public void CPUProcess(int result) {
        TARGET_WIDTH = ivCaptured.getWidth();
        TARGET_HEIGHT = ivCaptured.getHeight();
        ivCaptured.setImageBitmap(zoomBitmap(bmp));


        if (deploy_prototxt) {
            classificationDB.addClassification(Config.imagePath + "/" + Config.imageName[startIndex], String.valueOf((int) classificationTime), getFps(classificationTime), IMAGENET_CLASSES[result]);
        } else {
            classificationDB.addCPUSimpleClassification(Config.imagePath + "/" + Config.imageName[startIndex], String.valueOf((int) classificationTime), getFps(classificationTime), IMAGENET_CLASSES[result]);
        }

        startIndex++;
        testPro.setText("图片分类进行中...(" + startIndex + "%)");
        testResult.setText(getResources().getString(R.string.test_result) + IMAGENET_CLASSES[result]);
        testTime.setText(getResources().getString(R.string.test_time) + String.valueOf((int) classificationTime) + "ms");
        textFps.setText(getResources().getString(R.string.test_fps) + ConvertUtil.getFps(getFps(classificationTime)) + getResources().getString(R.string.test_fps_units));

        if (startIndex < Config.imageName.length) {
            executeImg();
        } else {
            Toast.makeText(this, R.string.end_testing, Toast.LENGTH_SHORT).show();
            testPro.setText("图片分类检测结束");
            isExist = false;
            classification_begin.setVisibility(View.VISIBLE);
            classification_end.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isExist = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isExist = false;
    }
}

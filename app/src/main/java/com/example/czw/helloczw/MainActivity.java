package com.example.czw.helloczw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.czw.helloczw.model.FileInfo;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String FileUrl = "http://www.imooc.com/mobile/appdown";
    private static final String FileName = "mukewang.apk";
    private TextView mTvFileName;
    private ProgressBar mPbProgress;
    private Button mBtStop;
    private Button mBtStart;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化组件
        mTvFileName = (TextView) findViewById(R.id.tvFileName);
        mPbProgress = (ProgressBar) findViewById(R.id.pbProgress);
        mBtStop = (Button) findViewById(R.id.btStop);
        mBtStart = (Button) findViewById(R.id.btStart);
        mPbProgress.setMax(100);
        //创建文件信息对象
        final FileInfo fileInfo = new FileInfo(0, FileUrl, FileName, 0, 0);
        mBtStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DownloadService.class);
                intent.setAction(DownloadService.DownloadStart);
                intent.putExtra("file_info", fileInfo);
                startService(intent);
            }
        });
        mBtStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DownloadService.class);
                intent.setAction(DownloadService.DownloadStop);
                intent.putExtra("file_info", fileInfo);
                startService(intent);
            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.DownloadUpdate);
        registerReceiver(mBReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mBReceiver);
        super.onDestroy();
    }

    BroadcastReceiver mBReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == DownloadService.DownloadUpdate) {
                int finish = intent.getIntExtra("finished", 0);
                mPbProgress.setProgress(finish);
            }
        }
    };
}

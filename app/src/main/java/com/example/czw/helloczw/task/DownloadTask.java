package com.example.czw.helloczw.task;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.example.czw.helloczw.DownloadService;
import com.example.czw.helloczw.model.FileInfo;
import com.example.czw.helloczw.model.ThreadInfo;
import com.example.czw.helloczw.provider.MyDatabaseManager;
import com.example.czw.helloczw.util.LogHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by czw on 2017/4/11.
 */

public class DownloadTask {
    private static final String Tag = "DownloadTast";
    private Context mContext;
    private FileInfo mFileInfo;
    private int mFinish;
    public boolean mPause;

    public DownloadTask(Context context, FileInfo fileInfo) {
        mContext = context;
        mFileInfo = fileInfo;
    }

    public void download() {
        List<ThreadInfo> list = new LinkedList<>();
        ThreadInfo threadInfo;
        String selection = MyDatabaseManager.MyDbColumns.NAME + " = ?";
        String[] selectionArgs = {mFileInfo.getUrl()};
        Cursor cursor = mContext.getContentResolver().query(
                MyDatabaseManager.MyDbColumns.CONTENT_URI, null, selection, selectionArgs,
                null);
        if (cursor == null || cursor.getCount() == 0) {
            threadInfo = new ThreadInfo(0, mFileInfo.getUrl(), 0, 0,
                    mFileInfo.getLength());
        } else {
            cursor.moveToFirst();
            {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(
                        MyDatabaseManager.MyDbColumns.UID));
                String url = cursor.getString(cursor.getColumnIndexOrThrow(
                        MyDatabaseManager.MyDbColumns.NAME));
                int start = cursor.getInt(cursor.getColumnIndexOrThrow(
                        MyDatabaseManager.MyDbColumns.START));
                int end = cursor.getInt(cursor.getColumnIndexOrThrow(
                        MyDatabaseManager.MyDbColumns.END));
                int finish = cursor.getInt(cursor.getColumnIndexOrThrow(
                        MyDatabaseManager.MyDbColumns.FINISHED));
                threadInfo = new ThreadInfo(id, url, finish, start, end);
                list.add(threadInfo);
            } while (cursor.moveToNext());

        }
        new DownloadThread(threadInfo).start();
    }

    class DownloadThread extends Thread {
        private ThreadInfo mThreadInfo;

        public DownloadThread(ThreadInfo threadInfo) {
            mThreadInfo = threadInfo;
        }

        public void run() {

            //向数据库插入线程信息
            String selection = MyDatabaseManager.MyDbColumns.UID + " = ? and " +
                    MyDatabaseManager.MyDbColumns.NAME + " = ?";
            String[] selectionArgs = {mThreadInfo.getId() + "", mThreadInfo.getUrl()};
            Cursor cursor = mContext.getContentResolver().query(
                    MyDatabaseManager.MyDbColumns.CONTENT_URI, null, selection, selectionArgs,
                    null);
            if (cursor == null || cursor.getCount() == 0) {
                LogHelper.i(Tag, "----cursor为0条记录");
                ContentValues values = new ContentValues();
                values.put(MyDatabaseManager.MyDbColumns.UID, mThreadInfo.getId());
                values.put(MyDatabaseManager.MyDbColumns.NAME, mThreadInfo.getUrl());
                values.put(MyDatabaseManager.MyDbColumns.START, mThreadInfo.getStart());
                values.put(MyDatabaseManager.MyDbColumns.END, mThreadInfo.getEnd());
                values.put(MyDatabaseManager.MyDbColumns.FINISHED, mThreadInfo.getFinished());
                mContext.getContentResolver().insert(MyDatabaseManager.MyDbColumns.CONTENT_URI,
                        values);
            }

            HttpURLConnection conn = null;
            RandomAccessFile raf = null;
            try {
                URL url = new URL(mThreadInfo.getUrl());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setRequestMethod("GET");
                //设置下载位置
                int start = mThreadInfo.getStart() + mThreadInfo.getFinished();
                conn.setRequestProperty("Range:", "bytes=" + start + "-" + mThreadInfo.getEnd());
                LogHelper.i(Tag, "start-end:" +"bytes=" + start + "-" + mThreadInfo.getEnd());
                //设置文件写入位置
                File file = new File(DownloadService.DownloadFile_path, mFileInfo.getFileName());
                raf = new RandomAccessFile(file, "rwd");
                raf.seek(start);
                Intent intent = new Intent(DownloadService.DownloadUpdate);
                mFinish += mThreadInfo.getFinished();
                //开始下载

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                    InputStream is = conn.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int length = -1;
                    long time = System.currentTimeMillis();
                    while((length = is.read(buffer)) != -1) {
                        raf.write(buffer, 0, length);
                        mFinish += length;

                        if (System.currentTimeMillis() - time > 500) {
                            LogHelper.i(Tag, mFinish + "");
                            time = System.currentTimeMillis();
                            intent.putExtra("finished", mFinish * 100 / mFileInfo.getLength());
                            mContext.sendBroadcast(intent);
                        }
                        //在下载暂停时保存下载进度
                        if (mPause) {
                            ContentValues values = new ContentValues();
                            String where = MyDatabaseManager.MyDbColumns.UID + " = ? and " +
                                    MyDatabaseManager.MyDbColumns.NAME + " = ?";
                            String[] args = new String[] {mThreadInfo.getId() + "", mThreadInfo
                            .getUrl()};
                            values.put(MyDatabaseManager.MyDbColumns.FINISHED,
                                    mFinish);

                            mContext.getContentResolver().update(MyDatabaseManager
                                    .MyDbColumns.CONTENT_URI, values, where, args);
                            return;
                        }
                    }
                    String where = MyDatabaseManager.MyDbColumns.UID + " = ? and " +
                            MyDatabaseManager.MyDbColumns.NAME + " = ?";
                    String[] args = new String[] {mThreadInfo.getId() + "", mThreadInfo
                            .getUrl()};
                    mContext.getContentResolver().delete(MyDatabaseManager
                            .MyDbColumns.CONTENT_URI, where, args);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null)
                    conn.disconnect();
                if (raf != null)
                    try {
                        raf.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
    }
}

package com.bailing.record.service;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.bailing.record.service.fftlib.ByteUtils;
import com.bailing.record.service.fftlib.FftFactory;
import com.bailing.record.service.file.FileUtils;
import com.bailing.record.service.listener.RecordDataListener;
import com.bailing.record.service.listener.RecordFftDataListener;
import com.bailing.record.service.listener.RecordResultListener;
import com.bailing.record.service.listener.RecordSoundSizeListener;
import com.bailing.record.service.listener.RecordStateListener;
import com.bailing.record.service.wav.WavUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author zhaolewei on 2018/7/10.
 */
public class RecordHelper {
    private static final String TAG = RecordHelper.class.getSimpleName();
    private volatile static RecordHelper instance;
    private volatile RecordState state = RecordState.IDLE;
    private static final int RECORD_AUDIO_BUFFER_TIMES = 1;

    private RecordStateListener recordStateListener;
    private RecordDataListener recordDataListener;
    private RecordSoundSizeListener recordSoundSizeListener;
    private RecordResultListener recordResultListener;
    private RecordFftDataListener recordFftDataListener;
    private RecordConfig currentConfig;
    private AudioRecordThread audioRecordThread;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private File resultFile = null;
    private File tmpFile = null;
    private List<File> files = new ArrayList<>();

    private RecordHelper() {
    }

    static RecordHelper getInstance() {
        if (instance == null) {
            synchronized (RecordHelper.class) {
                if (instance == null) {
                    instance = new RecordHelper();
                }
            }
        }
        return instance;
    }

    RecordState getState() {
        return state;
    }

    void setRecordStateListener(RecordStateListener recordStateListener) {
        this.recordStateListener = recordStateListener;
    }

    void setRecordDataListener(RecordDataListener recordDataListener) {
        this.recordDataListener = recordDataListener;
    }

    void setRecordSoundSizeListener(RecordSoundSizeListener recordSoundSizeListener) {
        this.recordSoundSizeListener = recordSoundSizeListener;
    }

    void setRecordResultListener(RecordResultListener recordResultListener) {
        this.recordResultListener = recordResultListener;
    }

    public void setRecordFftDataListener(RecordFftDataListener recordFftDataListener) {
        this.recordFftDataListener = recordFftDataListener;
    }

    public void start(String filePath, RecordConfig config) {
        this.currentConfig = config;
        if (state != RecordState.IDLE && state != RecordState.STOP) {
            return;
        }
        resultFile = new File(filePath);
        String tempFilePath = getTempFilePath();

        tmpFile = new File(tempFilePath);
        audioRecordThread = new AudioRecordThread();
        audioRecordThread.start();
    }

    public void stop() {
        if (state == RecordState.IDLE) {
            return;
        }

        if (state == RecordState.PAUSE) {
            makeFile();
            state = RecordState.IDLE;
            notifyState();
        } else {
            state = RecordState.STOP;
            notifyState();
        }
    }

    void pause() {
        if (state != RecordState.RECORDING) {
            return;
        }
        state = RecordState.PAUSE;
        notifyState();
    }

    void resume() {
        if (state != RecordState.PAUSE) {
            return;
        }
        String tempFilePath = getTempFilePath();
        tmpFile = new File(tempFilePath);
        audioRecordThread = new AudioRecordThread();
        audioRecordThread.start();
    }

    private void notifyState() {
        if (recordStateListener == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                recordStateListener.onStateChange(state);
            }
        });

        if (state == RecordState.STOP || state == RecordState.PAUSE) {
            if (recordSoundSizeListener != null) {
                recordSoundSizeListener.onSoundSize(0);
            }
        }
    }

    private void notifyFinish() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (recordStateListener != null) {
                    recordStateListener.onStateChange(RecordState.FINISH);
                }
                if (recordResultListener != null) {
                    recordResultListener.onResult(resultFile);
                }
            }
        });
    }

    private void notifyError(final String error) {
        if (recordStateListener == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                recordStateListener.onError(error);
            }
        });
    }

    private FftFactory fftFactory = new FftFactory(FftFactory.Level.Original);

    private void notifyData(final byte[] data) {
        if (recordDataListener == null && recordSoundSizeListener == null && recordFftDataListener == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (recordDataListener != null) {
                    recordDataListener.onData(data);
                }

                if (recordFftDataListener != null || recordSoundSizeListener != null) {
                    byte[] fftData = fftFactory.makeFftData(data);
                    if (fftData != null) {
                        if (recordSoundSizeListener != null) {
                            recordSoundSizeListener.onSoundSize(getDb(fftData));
                        }
                        if (recordFftDataListener != null) {
                            recordFftDataListener.onFftData(fftData);
                        }
                    }
                }
            }
        });
    }

    private int getDb(byte[] data) {
        double sum = 0;
        double ave;
        int length = data.length > 128 ? 128 : data.length;
        int offsetStart = 8;
        for (int i = offsetStart; i < length; i++) {
            sum += data[i];
        }
        ave = (sum / (length - offsetStart)) * 65536 / 128f;
        int i = (int) (Math.log10(ave) * 20);
        return i < 0 ? 27 : i;
    }

    private class AudioRecordThread extends Thread {
        private AudioRecord audioRecord;
        private int bufferSize;

        AudioRecordThread() {
            bufferSize = AudioRecord.getMinBufferSize(currentConfig.getSampleRate(),
                    currentConfig.getChannelConfig(), currentConfig.getEncodingConfig()) * RECORD_AUDIO_BUFFER_TIMES;
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, currentConfig.getSampleRate(),
                    currentConfig.getChannelConfig(), currentConfig.getEncodingConfig(), bufferSize);
        }

        @Override
        public void run() {
            super.run();
            switch (currentConfig.getFormat()) {
                default:
                    startPcmRecorder();
                    break;
            }
        }

        private void startPcmRecorder() {
            state = RecordState.RECORDING;
            notifyState();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tmpFile);
                audioRecord.startRecording();
                byte[] byteBuffer = new byte[bufferSize];

                while (state == RecordState.RECORDING) {
                    int end = audioRecord.read(byteBuffer, 0, byteBuffer.length);
                    notifyData(byteBuffer);
                    fos.write(byteBuffer, 0, end);
                    fos.flush();
                }
                audioRecord.stop();
                files.add(tmpFile);
                if (state == RecordState.STOP) {
                    makeFile();
                } else {
                }
            } catch (Exception e) {
                notifyError("录音失败");
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (state != RecordState.PAUSE) {
                state = RecordState.IDLE;
                notifyState();
            }
        }

        private void startMp3Recorder() {
            state = RecordState.RECORDING;
            notifyState();

            try {
                audioRecord.startRecording();
                short[] byteBuffer = new short[bufferSize];

                while (state == RecordState.RECORDING) {
                    int end = audioRecord.read(byteBuffer, 0, byteBuffer.length);
                    notifyData(ByteUtils.toBytes(byteBuffer));
                }
                audioRecord.stop();
            } catch (Exception e) {
                notifyError("录音失败");
            }
            if (state != RecordState.PAUSE) {
                state = RecordState.IDLE;
                notifyState();
            } else {
            }
        }
    }

    private void makeFile() {
        switch (currentConfig.getFormat()) {
            case WAV:
                mergePcmFile();
                makeWav();
                break;
            case PCM:
                mergePcmFile();
                break;
            default:
                break;
        }
        notifyFinish();
    }

    /**
     * 添加Wav头文件
     */
    private void makeWav() {
        if (!FileUtils.isFile(resultFile) || resultFile.length() == 0) {
            return;
        }
        byte[] header = WavUtils.generateWavFileHeader((int) resultFile.length(), currentConfig.getSampleRate(), currentConfig.getChannelCount(), currentConfig.getEncoding());
        WavUtils.writeHeader(resultFile, header);
    }

    /**
     * 合并文件
     */
    private void mergePcmFile() {
        boolean mergeSuccess = mergePcmFiles(resultFile, files);
        if (!mergeSuccess) {
            notifyError("合并失败");
        }
    }

    /**
     * 合并Pcm文件
     *
     * @param recordFile 输出文件
     * @param files      多个文件源
     * @return 是否成功
     */
    private boolean mergePcmFiles(File recordFile, List<File> files) {
        if (recordFile == null || files == null || files.size() <= 0) {
            return false;
        }

        FileOutputStream fos = null;
        BufferedOutputStream outputStream = null;
        byte[] buffer = new byte[1024];
        try {
            fos = new FileOutputStream(recordFile);
            outputStream = new BufferedOutputStream(fos);

            for (int i = 0; i < files.size(); i++) {
                BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(files.get(i)));
                int readCount;
                while ((readCount = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, readCount);
                }
                inputStream.close();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < files.size(); i++) {
            files.get(i).delete();
        }
        files.clear();
        return true;
    }

    /**
     * 根据当前的时间生成相应的文件名
     * 实例 record_20160101_13_15_12
     */
    private String getTempFilePath() {
        String fileDir = String.format(Locale.getDefault(), "%s/Record/", Environment.getExternalStorageDirectory().getAbsolutePath());
        if (!FileUtils.createOrExistsDir(fileDir)) {
            Log.e(TAG, "文件夹创建失败：%s" + fileDir);
        }
        String fileName = String.format(Locale.getDefault(), "%s", FileUtils.getNowString(new SimpleDateFormat("yyyyMMddHHmmss", Locale.SIMPLIFIED_CHINESE)));
        return String.format(Locale.getDefault(), "%s%s.pcm", fileDir, fileName);
    }

    /**
     * 表示当前状态
     */
    public enum RecordState {
        /**
         * 空闲状态
         */
        IDLE,
        /**
         * 录音中
         */
        RECORDING,
        /**
         * 暂停中
         */
        PAUSE,
        /**
         * 正在停止
         */
        STOP,
        /**
         * 录音流程结束（转换结束）
         */
        FINISH
    }
}

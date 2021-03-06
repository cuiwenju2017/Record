package com.bailing.record.acivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bailing.record.BuildConfig;
import com.bailing.record.MyApplication;
import com.bailing.record.R;
import com.bailing.record.adapter.BaseRVAdapter;
import com.bailing.record.adapter.BaseRVHolder;
import com.bailing.record.bean.PcmBean;
import com.bailing.record.bean.PcmLongBean;
import com.bailing.record.databinding.ActivityMainBinding;
import com.bailing.record.record.EncoderC;
import com.bailing.record.record.FileUtil;
import com.bailing.record.service.RecordConfig;
import com.bailing.record.service.RecordManager;
import com.bailing.record.service.TimeUtils;
import com.bailing.record.service.config.Constant;
import com.bailing.record.service.event.RecorderEvent;
import com.bailing.record.utils.OneClickThree;
import com.bailing.record.utils.TimeUtil;
import com.lodz.android.core.utils.NotificationUtils;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.litepal.LitePal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.bailing.record.record.FileUtil.AUDIO_PCM_BASEPATH;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ActivityMainBinding binding;

    private boolean pause = false;
    private static final int GET_RECODE_AUDIO = 1;
    //????????????
    private int AUDIO_SAMPLE_RATE = 8000, AUDIO_SAMPLE_RATE2;//8k
    //??????
    private int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;//16bit???
    //?????? ?????????
    private int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;//?????????
    private static String[] PERMISSION_ALL = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private String ypgs = ".wav";
    private int p = 1;
    private short bit = 16;
    private int bit2;
    private short sd = 1;
    private int sd2;
    private int selectedPosition = -1;
    private int max;
    private boolean add;
    private long recordTime = 0;
    private long id;
    private int pos;
    private int a = 1;
    private String fileTiem;
    private String pathPcm;
    private boolean isStart = false;
    private boolean isPause = false;
    private File file;
    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ByteArrayOutputStream byteArrayOutputStream2 = null;
    private RecordManager recordManager = RecordManager.getInstance();
    private BasePopupView basePopupView;
    private MediaPlayer mediaPlayer2;
    private String fileName, fileName2;
    private BaseRVAdapter<PcmLongBean> adapter;
    private BaseRVAdapter<PcmBean> adapter2;
    private List<PcmLongBean> list = new ArrayList<>();
    private List<PcmBean> pcmBeanList = new ArrayList<>();
    private List<PcmBean> pcmBeans = new ArrayList<>();
    private List<PcmBean> pcmBeans2 = new ArrayList<>();
    private PcmLongBean pcmLongBean;
    private Runnable runnable;
    private Handler handlerPcm = new Handler();
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //????????????????????????????????????
        LitePal.getDatabase();

        initView();
        initData();
        initFile();
    }

    private void initView() {
        binding.tvCyl.setOnClickListener(this);
        binding.tvBit.setOnClickListener(this);
        binding.tvSd.setOnClickListener(this);
        binding.tvYpgs.setOnClickListener(this);
        binding.btnStart.setOnClickListener(this);
        binding.btnPause.setOnClickListener(this);
        binding.btnStop.setOnClickListener(this);
    }

    private void initData() {
        //??????EventBus
        EventBus.getDefault().register(this);
        //????????????
        registerReceiver(mHomeKeyEventReceiver, new IntentFilter(
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    /**
     * ??????PendingIntent?????????
     */
    public static PendingIntent startPendingIntent(Context context, String data) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(Constant.EXTRA_MSG_DATA, data);
        return PendingIntent.getActivity(context, UUID.randomUUID().hashCode(), intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * ?????????RecordManager
     **/
    private void initRecord() {
        recordManager.init(MyApplication.getInstance(), BuildConfig.DEBUG);
        //?????????
        recordManager.changeRecordConfig(recordManager.getRecordConfig().setSampleRate(AUDIO_SAMPLE_RATE));
        //Bit??????
        recordManager.changeRecordConfig(recordManager.getRecordConfig().setEncodingConfig(AUDIO_ENCODING));
        //??????
        recordManager.changeRecordConfig(recordManager.getRecordConfig().setChannelConfig(AUDIO_CHANNEL));
        //????????????
        recordManager.changeFormat(RecordConfig.RecordFormat.WAV);

        //RecordManager??????
        initRecordEvent();
    }

    /**
     * RecordManager??????
     **/
    private void initRecordEvent() {
        recordManager.setRecordDataListener(data -> {
            try {
                byteArrayOutputStream.write(data);
                byteArrayOutputStream2.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void initFile() {
        adapter = new BaseRVAdapter<PcmLongBean>(R.layout.item_pcm_list) {
            @Override
            public void onBindVH(BaseRVHolder holder, PcmLongBean data, int position) {
                LinearLayout ll = holder.getView(R.id.ll);
                Button btn_zhuanhuan = holder.getView(R.id.btn_zhuanhuan);
                RecyclerView rv_child_pcm = holder.getView(R.id.rv_child_pcm);
                ImageView iv_play = holder.getView(R.id.iv_play);
                ImageView iv_stop = holder.getView(R.id.iv_stop);
                ProgressBar pb = holder.getView(R.id.pb);
                LinearLayout ll_jindu = holder.getView(R.id.ll_jindu);
                TextView tv_time = holder.getView(R.id.tv_time);
                ImageView iv = holder.getView(R.id.iv);

                holder.setText(R.id.adapter_file_list_name, data.getName());//???????????????
                holder.setText(R.id.adapter_file_list_create_date,
                        data.getTime());//????????????
                holder.setText(R.id.adapter_file_list_create_size,
                        data.getFileSize());//??????????????????
                holder.setText(R.id.tv_path, data.getPath());//????????????

                if (data.isZhuanhuan()) {
                    btn_zhuanhuan.setVisibility(View.GONE);

                    MediaPlayer mediaPlayer = new MediaPlayer();
                    try {
                        mediaPlayer.setDataSource(list.get(position).getPath());
                        mediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    long time = mediaPlayer.getDuration() / 1000;
                    holder.setText(R.id.adapter_file_list_create_duration, TimeUtil.getTime(time));//??????????????????

                    iv_play.setVisibility(View.VISIBLE);

                    if (selectedPosition == position && !add) {
                        selectedPosition = -1;
                        ll_jindu.setVisibility(View.VISIBLE);
                        iv_play.setVisibility(View.GONE);
                        iv_stop.setVisibility(View.VISIBLE);

                        max = Integer.parseInt(String.valueOf(time));//????????????
                        pb.setMax(max);//????????????????????????
                        p = 0;//??????????????????
                        pb.setProgress(0);//???????????????
                        //???????????????????????????
                        runnable = new Runnable() {
                            @SuppressLint("SetTextI18n")
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            public void run() {
                                pb.setProgress(p++);//??????????????????1
                                tv_time.setText("" + TimeUtil.getTime(p));
                                //???????????????????????????????????????????????????????????????????????????????????????
                                if (p > max) {
                                    ll_jindu.setVisibility(View.GONE);
                                    iv_play.setVisibility(View.VISIBLE);
                                    iv_stop.setVisibility(View.GONE);
                                }
                                handler.postDelayed(this, 1000); //n???????????????
                            }
                        };
                        //????????????
                        handler.postDelayed(runnable, 1000);
                    } else {
                        ll_jindu.setVisibility(View.GONE);
                        iv_play.setVisibility(View.VISIBLE);
                        iv_stop.setVisibility(View.GONE);
                    }
                } else {
                    btn_zhuanhuan.setVisibility(View.VISIBLE);
                    holder.setText(R.id.adapter_file_list_create_duration, "");//??????????????????
                }

                iv_play.setOnClickListener(v -> {//????????????
                    if (isStart || isPause) {
                        Toast.makeText(mContext, "????????????...???????????????????????????", Toast.LENGTH_SHORT).show();
                    } else {
                        //?????????????????????????????????
                        if (mediaPlayer2 != null && mediaPlayer2.isPlaying()) {
                            mediaPlayer2.stop();
                        }
                        //????????????
                        handler.removeCallbacks(runnable);

                        add = false;
                        //????????????
                        clearSelection(position);
                        adapter.notifyDataSetChanged();

                        try {
                            //?????????????????????
                            mediaPlayer2 = new MediaPlayer();
                            mediaPlayer2.setDataSource(list.get(position).getPath());
                            mediaPlayer2.prepare();
                            mediaPlayer2.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                iv_stop.setOnClickListener(v -> {//????????????
                    ll_jindu.setVisibility(View.GONE);//???????????????
                    iv_play.setVisibility(View.VISIBLE);//????????????????????????
                    iv_stop.setVisibility(View.GONE);//????????????????????????
                    //??????????????????
                    mediaPlayer2.stop();
                    //????????????
                    handler.removeCallbacks(runnable);
                });

                ll.setOnLongClickListener(v -> {//??????????????????
                    new XPopup.Builder(MainActivity.this).asConfirm("??????",
                            "????????????????????????",
                            () -> {
                                String wenjianjiaUri = Environment.getExternalStorageDirectory().getAbsolutePath()
                                        + AUDIO_PCM_BASEPATH + data.getName().substring(0, data.getName()
                                        .lastIndexOf("."));
                                boolean isDelte = FileUtil.deleteDirectory(wenjianjiaUri);
                                if (isDelte) {
                                    //??????id??????
                                    LitePal.delete(PcmLongBean.class, data.getId());
                                    adapter.remove(position);
                                } else {
                                    Toast.makeText(MainActivity.this, "????????????",
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                    return false;
                });

                btn_zhuanhuan.setOnClickListener(v -> {//PCM???WAV
                    pos = position;
                    id = data.getId();
                    fileTiem = data.getTime();
                    fileName2 = data.getName().substring(0, data.getName().lastIndexOf("."));
                    pathPcm = data.getPath();
                    pcmBeans2 = data.getPcmBeans();
                    AUDIO_SAMPLE_RATE2 = data.getCyl();
                    sd2 = data.getSd();
                    bit2 = data.getBit();

                    basePopupView = new XPopup.Builder(MainActivity.this)
                            .dismissOnTouchOutside(false) // ??????????????????????????????????????????true
                            .asLoading("?????????...")
                            .show();
                    new Thread(() -> {
                        if (EncoderC.copyWaveFile(AUDIO_SAMPLE_RATE2, sd2, pathPcm,
                                FileUtil.getWavFileAbsolutePath(fileName2, ypgs, fileName2))) {
                            Message msg = new Message();
                            msg.obj = 0;
                            mHandler.sendMessage(msg);
                        } else {
                            Message msg = new Message();
                            msg.obj = 1;
                            mHandler.sendMessage(msg);
                        }
                    }).start();
                });

                if (data.getPcmBeans().size() > 0) {
                    iv.setVisibility(View.VISIBLE);
                } else {
                    iv.setVisibility(View.GONE);
                }

                if (data.isZhankai()) {
                    rv_child_pcm.setVisibility(View.VISIBLE);
                    iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_zhankan));
                } else {
                    rv_child_pcm.setVisibility(View.GONE);
                    iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_zhedie));
                }

                ll.setOnClickListener(v -> {
                    //???????????????
                    if (!data.isZhankai()) {
                        rv_child_pcm.setVisibility(View.VISIBLE);
                        data.setZhankai(true);
                        iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_zhankan));
                    } else {
                        rv_child_pcm.setVisibility(View.GONE);
                        data.setZhankai(false);
                        iv.setImageDrawable(getResources().getDrawable(R.drawable.icon_zhedie));
                    }
                });

                adapter2 = new BaseRVAdapter<PcmBean>(R.layout.item_pcm_child_list) {
                    @Override
                    public void onBindVH(BaseRVHolder holder, PcmBean data2, int position) {
                        TextView tv_zhuanhuan = holder.getView(R.id.tv_zhuanhuan);
                        holder.setText(R.id.adapter_file_list_name, data2.getName());//???????????????
                        holder.setText(R.id.adapter_file_list_create_date,
                                data2.getTime());//????????????
                        holder.setText(R.id.adapter_file_list_create_size,
                                data2.getFileSize());//??????????????????
                        holder.setText(R.id.tv_path, data2.getPath());

                        if (data2.getZhuanhuan()) {
                            tv_zhuanhuan.setVisibility(View.VISIBLE);
                        } else {
                            tv_zhuanhuan.setVisibility(View.GONE);
                        }
                    }
                };
                //???????????????
                rv_child_pcm.setAdapter(adapter2);
                rv_child_pcm.setLayoutManager(new GridLayoutManager(MainActivity.this, 1));//????????????
                pcmBeanList = data.getPcmBeans();
                adapter2.setNewData(pcmBeanList);
            }
        };

        //???????????????
        View empty = LayoutInflater.from(this).inflate(R.layout.layout_empty_view, null,
                false);
        adapter.setEmptyView(empty);

        //???????????????
        binding.rvPcmList.setAdapter(adapter);
        binding.rvPcmList.setLayoutManager(new GridLayoutManager(this, 1));//????????????

        //???????????????????????????
        String fileBasePath = Environment.getExternalStorageDirectory().getAbsolutePath() + AUDIO_PCM_BASEPATH;
        File file = new File(fileBasePath);
        //????????????
        if (!file.exists()) {
            file.mkdirs();
        }
        if (file.exists() && file.isDirectory() && file.list() != null) {
            if (file.list().length > 0) {//?????????????????????
                //??????????????????
//                list = LitePal.findAll(PcmLongBean.class);//?????????
                list = LitePal.order("Time desc").find(PcmLongBean.class, true);//????????????
                adapter.setNewData(list);
            } else {//???????????????
                LitePal.deleteAll(PcmLongBean.class);
                LitePal.deleteAll(PcmBean.class);
            }
        }

        //????????????????????????(??????????????????)
        boolean isDelect = FileUtil.deleteDirectory2(fileBasePath);
        if (isDelect) {
            Log.i("aaa", "initFile: ????????????");
        }
    }

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    mHandler.postDelayed(() -> {
                        //????????????
                        basePopupView.dismiss();
                        Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                        PcmLongBean pcmLongBean2 = LitePal.find(PcmLongBean.class, id);

                        //????????????pcm
                        File fileZ2 = new File(pathPcm);
                        PcmBean pcmBean2 = new PcmBean();
                        pcmBean2.setTime(FileUtil.getFileLastModifiedTime(fileZ2));
                        pcmBean2.setFileSize(FileUtil.FormetFileSize(fileZ2.length()));
                        pcmBean2.setName(fileZ2.getName());
                        pcmBean2.setPath(fileZ2.getPath());
                        pcmBean2.setCyl(AUDIO_SAMPLE_RATE2);
                        pcmBean2.setBit(bit2);
                        pcmBean2.setSd(sd2);
                        pcmBean2.setZhuanhuan(true);
                        pcmBean2.save();
                        pcmBeans2.add(pcmBean2);

                        String filePath = FileUtil.getWavFileAbsolutePath(fileName2, ypgs, fileName2);
                        File fileWav2 = new File(filePath);
                        pcmLongBean2.setTime(fileTiem);
                        pcmLongBean2.setFileSize(FileUtil.FormetFileSize(fileWav2.length()));
                        pcmLongBean2.setName(fileWav2.getName());
                        pcmLongBean2.setPath(fileWav2.getPath());
                        pcmLongBean2.setZhuanhuan(true);
                        pcmLongBean2.setPcmBeans(pcmBeans2);
                        pcmLongBean2.save();

                        adapter.getData().get(pos).setTime(fileTiem);
                        adapter.getData().get(pos).setFileSize(FileUtil.FormetFileSize(fileWav2.length()));
                        adapter.getData().get(pos).setName(fileWav2.getName());
                        adapter.getData().get(pos).setPath(fileWav2.getPath());
                        adapter.getData().get(pos).setZhuanhuan(true);
                        adapter.getData().get(pos).setPcmBeans(pcmBeans2);
                        adapter.notifyItemChanged(pos);
                    }, 1000 * 2);
                    break;
                case 1:
                    mHandler.postDelayed(() -> {
                        //????????????
                        basePopupView.dismiss();
                        Toast.makeText(MainActivity.this, "????????????", Toast.LENGTH_SHORT).show();
                    }, 1000 * 2);
                    break;
                default:
                    break;
            }
        }
    };

    public void clearSelection(int position) {
        selectedPosition = position;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_cyl://?????????
                new XPopup.Builder(this)
                        //.maxWidth(600)
                        .asCenterList("???????????????", new String[]{"8k", "16k"},
                                (position, text) -> {
                                    binding.tvCyl.setText(text);
                                    if ("8k".equals(text)) {
                                        AUDIO_SAMPLE_RATE = 8000;
                                    } else if ("16k".equals(text)) {
                                        AUDIO_SAMPLE_RATE = 16000;
                                    }
                                })
                        .show();
                break;
            case R.id.tv_bit://bit???
                new XPopup.Builder(this)
                        //.maxWidth(600)
                        .asCenterList("???????????????", new String[]{"8bit", "16bit"},
                                (position, text) -> {
                                    binding.tvBit.setText(text);
                                    if ("8bit".equals(text)) {
                                        AUDIO_ENCODING = AudioFormat.ENCODING_PCM_8BIT;
                                        bit = 8;
                                    } else if ("16bit".equals(text)) {
                                        AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
                                        bit = 16;
                                    }
                                })
                        .show();
                break;
            case R.id.tv_sd://??????
                new XPopup.Builder(this)
                        //.maxWidth(600)
                        .asCenterList("???????????????", new String[]{"?????????", "?????????"},
                                (position, text) -> {
                                    binding.tvSd.setText(text);
                                    if ("?????????".equals(text)) {
                                        AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
                                        sd = 1;
                                    } else if ("?????????".equals(text)) {
                                        AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
                                        sd = 2;
                                    }
                                })
                        .show();
                break;
            case R.id.tv_ypgs://????????????
                new XPopup.Builder(this)
                        //.maxWidth(600)
                        .asCenterList("???????????????", new String[]{"WAV", "MP3", "AAC", "AMR", "FLAC", "MIDI", "OGG"},
                                (position, text) -> {
                                    binding.tvYpgs.setText(text);
                                    if ("WAV".equals(text)) {
                                        ypgs = ".wav";
                                    } else if ("MP3".equals(text)) {
                                        ypgs = ".mp3";
                                    } else if ("AAC".equals(text)) {
                                        ypgs = ".aac";
                                    } else if ("AMR".equals(text)) {
                                        ypgs = ".amr";
                                    } else if ("FLAC".equals(text)) {
                                        ypgs = ".flac";
                                    } else if ("MIDI".equals(text)) {
                                        ypgs = ".midi";
                                    } else if ("OGG".equals(text)) {
                                        ypgs = ".ogg";
                                    }
                                })
                        .show();
                break;
            case R.id.btn_start://????????????
                if (!OneClickThree.isFastClick()) {
                    verifyPermissions(this);
                }
                break;
            case R.id.btn_pause://????????????
                if (isStart || isPause) {
                    binding.tvCyl.setEnabled(false);
                    binding.tvBit.setEnabled(false);
                    binding.tvSd.setEnabled(false);
                    binding.tvYpgs.setEnabled(false);

                    if (!pause) {
                        binding.btnPause.setText("??????");
                        //????????????
                        recordManager.pause();
                        isPause = true;
                        isStart = false;
                        //????????????
                        handlerPcm.removeCallbacks(runnablePcm);
                        //????????????pcm
                        String fName = FileUtil.getPcmFileAbsolutePath(fileName + "_" + a++, fileName);
                        FileUtil.saveBytesToFile(fName, byteArrayOutputStream.toByteArray());
                        File file2 = new File(fName);
                        savePcmData(file2, false);
                        //????????????pcm
                        file = new File(FileUtil.getPcmFileAbsolutePath(fileName, fileName));
                        saveData(file, false);
                        pause = true;
                    } else {
                        binding.btnPause.setText("??????");
                        //????????????
                        recordManager.resume();
                        isPause = false;
                        isStart = true;
                        //30s????????????pcm
                        handlerPcm.postDelayed(runnablePcm, 1000 * 30);
                        pause = false;
                    }
                } else {
                    Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_stop://????????????
                if (isStart || isPause) {
                    if (!OneClickThree.isFastClick()) {
                        stopRecord();
                    }
                } else {
                    Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    /**
     * ????????????
     */
    private void stopRecord() {
        binding.tvCyl.setEnabled(true);
        binding.tvBit.setEnabled(true);
        binding.tvSd.setEnabled(true);
        binding.tvYpgs.setEnabled(true);
        binding.btnStart.setEnabled(true);

        //????????????
        doStop();
        binding.btnStart.setText("??????");
        //????????????
        handlerPcm.removeCallbacks(runnablePcm);
        recordTime = 0;

        basePopupView = new XPopup.Builder(this)
                .dismissOnTouchOutside(false) // ??????????????????????????????????????????true
                .asLoading("?????????...")
                .show();

        new Thread(() -> {
            String pathPcm = FileUtil.getPcmFileAbsolutePath(fileName, fileName);
            //??????pcm?????????
            FileUtil.saveBytesToFile(pathPcm, byteArrayOutputStream2.toByteArray());
            if (EncoderC.copyWaveFile(AUDIO_SAMPLE_RATE, sd, pathPcm,
                    FileUtil.getWavFileAbsolutePath(fileName, ypgs, fileName))) {//pcm???wav
                if (byteArrayOutputStream.size() > 0) {
                    //????????????pcm
                    String fName = FileUtil.getPcmFileAbsolutePath(fileName + "_" + a++, fileName);
                    FileUtil.saveBytesToFile(fName, byteArrayOutputStream.toByteArray());
                    File file2 = new File(fName);
                    savePcmData(file2, false);
                }

                //????????????pcm
                File fileZ = new File(pathPcm);
                savePcmData(fileZ, true);

                //????????????wav
                String filePath = FileUtil.getWavFileAbsolutePath(fileName, ypgs, fileName);
                File fileWav = new File(filePath);
                saveData(fileWav, true);
            } else {
                //????????????pcm
                String fName = FileUtil.getPcmFileAbsolutePath(fileName + "_" + a++, fileName);
                FileUtil.saveBytesToFile(fName, byteArrayOutputStream.toByteArray());
                File file2 = new File(fName);
                savePcmData(file2, false);

                //????????????pcm
                String pcmPath = FileUtil.getPcmFileAbsolutePath(fileName, fileName);
                File filePcm = new File(pcmPath);
                saveData(filePcm, false);
            }
        }).start();

        //????????????
        new Handler().postDelayed(() -> {
            add = true;
            initFile();
            basePopupView.dismiss();
        }, 1000 * 2);

        if (mediaPlayer2 != null && mediaPlayer2.isPlaying()) {
            mediaPlayer2.release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GET_RECODE_AUDIO && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
            startRecord();
        }
    }

    /**
     * ??????????????????
     */
    public void verifyPermissions(Activity activity) {
        boolean permission = (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED);
        if (permission) {
            ActivityCompat.requestPermissions(activity, PERMISSION_ALL,
                    GET_RECODE_AUDIO);
        } else {
            startRecord();
        }
    }

    /**
     * ????????????
     */
    private void startRecord() {
        if (mediaPlayer2 != null && mediaPlayer2.isPlaying()) {
            Toast.makeText(this, "????????????...??????????????????????????????", Toast.LENGTH_SHORT).show();
        } else {
            binding.tvCyl.setEnabled(false);
            binding.tvBit.setEnabled(false);
            binding.tvSd.setEnabled(false);
            binding.tvYpgs.setEnabled(false);
            binding.btnStart.setEnabled(false);

            fileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());//????????????????????????
            byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream2 = new ByteArrayOutputStream();
            pcmLongBean = new PcmLongBean();
            pcmBeans.clear();
            a = 1;
            //???????????????
            initRecord();
            //????????????
            recordManager.start();
            isStart = true;
            isPause = false;
            binding.btnStart.setText("?????????00:00:00");
            //30s????????????pcm
            handlerPcm.postDelayed(runnablePcm, 1000 * 30);
        }
    }

    /**
     * ??????  ??????
     **/
    private void doStop() {
        recordManager.stop();
        isStart = false;
        isPause = false;
        NotificationUtils.create(this).getManager().cancel(Constant.NOTIFI_RECORDER_ID);
    }

    //30s????????????pcm
    Runnable runnablePcm = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        public void run() {
            //????????????pcm
            String fName = FileUtil.getPcmFileAbsolutePath(fileName + "_" + a++, fileName);
            FileUtil.saveBytesToFile(fName, byteArrayOutputStream.toByteArray());
            File file2 = new File(fName);
            savePcmData(file2, false);

            //????????????pcm
            String pathPcm = FileUtil.getPcmFileAbsolutePath(fileName, fileName);
            FileUtil.saveBytesToFile(pathPcm, byteArrayOutputStream2.toByteArray());
            file = new File(FileUtil.getPcmFileAbsolutePath(fileName, fileName));
            saveData(file, false);

            handlerPcm.postDelayed(this, 1000 * 30); //n???????????????
        }
    };

    //????????????pcm
    private void savePcmData(File file, Boolean b) {
        PcmBean pcmBean = new PcmBean();
        pcmBean.setTime(FileUtil.getFileLastModifiedTime(file));
        pcmBean.setFileSize(FileUtil.FormetFileSize(file.length()));
        pcmBean.setName(file.getName());
        pcmBean.setPath(file.getPath());
        pcmBean.setCyl(AUDIO_SAMPLE_RATE);
        pcmBean.setBit(bit);
        pcmBean.setSd(sd);
        pcmBean.setZhuanhuan(b);
        pcmBean.save();
        pcmBeans.add(pcmBean);
        byteArrayOutputStream = new ByteArrayOutputStream();
    }

    //????????????pcm???wav
    private void saveData(File file, boolean b) {
        pcmLongBean.setTime(FileUtil.getFileLastModifiedTime(file));
        pcmLongBean.setFileSize(FileUtil.FormetFileSize(file.length()));
        pcmLongBean.setName(file.getName());
        pcmLongBean.setPath(file.getPath());
        pcmLongBean.setCyl(AUDIO_SAMPLE_RATE);
        pcmLongBean.setBit(bit);
        pcmLongBean.setSd(sd);
        pcmLongBean.setZhuanhuan(b);
        pcmLongBean.setPcmBeans(pcmBeans);
        pcmLongBean.save();
    }

    /**
     * ?????????????????????home???????????????????????????
     */
    private BroadcastReceiver mHomeKeyEventReceiver = new BroadcastReceiver() {

        String SYSTEM_REASON = "reason";
        String SYSTEM_HOME_KEY = "homekey";
        String SYSTEM_HOME_KEY_LONG = "recentapps";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra(SYSTEM_REASON);
                if (TextUtils.equals(reason, SYSTEM_HOME_KEY)) {
                    //????????????home???,??????????????????
                    if (isStart || isPause) {
                        Toast.makeText(MainActivity.this, "???????????????,?????????????????????????????????", Toast.LENGTH_SHORT).show();
                    }
                } else if (TextUtils.equals(reason, SYSTEM_HOME_KEY_LONG)) {
                    //????????????home???,?????????????????????????????????
                    if (isStart || isPause) {
                        Toast.makeText(MainActivity.this, "???????????????,?????????????????????????????????", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    public void onBackPressed() {
        if (isStart || isPause) {
            basePopupView = new XPopup.Builder(this).asConfirm("??????", "??????????????????????????????????????????",
                    () -> {
                        //??????pcm?????????
                        FileUtil.saveBytesToFile(FileUtil.getPcmFileAbsolutePath(fileName, fileName), byteArrayOutputStream2.toByteArray());
                        stopRecord();
                        finish();
                    })
                    .show();
            //????????????????????????????????????
//            moveTaskToBack(false);
            //????????????????????????????????????
            /*Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);*/
        } else if (mediaPlayer2 != null && mediaPlayer2.isPlaying()) {
            basePopupView = new XPopup.Builder(this).asConfirm("??????", "??????????????????????????????????????????",
                    () -> {
                        mediaPlayer2.stop();
                        mediaPlayer2.release();
                        finish();
                    })
                    .show();
        } else {
            finish();
        }
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRecorderEvent(RecorderEvent event) {
        //??????????????????????????? eventbus
        if (event == null) {
            return;
        }

        binding.btnStart.setText("?????????" + TimeUtils.getGapTime(event.timeCounter));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //??????EventBus
        EventBus.getDefault().unregister(this);
    }
}
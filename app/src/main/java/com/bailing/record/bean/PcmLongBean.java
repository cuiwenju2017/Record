package com.bailing.record.bean;

import org.litepal.crud.LitePalSupport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PcmLongBean extends LitePalSupport {

    private long id;
    private int cyl;//采样率
    private int bit; //bit位
    private int sd;//声道
    private File file;//文件
    private String Name;//文件名
    private String Path;//文件路径
    private String Time;//文件日期
    private String FileSize;//文件大小
    private boolean isZhuanhuan;//是否转换
    private List<PcmBean> pcmBeans = new ArrayList<>();//完整文件下的分段原文件列表
    private boolean isZhankai;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getCyl() {
        return cyl;
    }

    public void setCyl(int cyl) {
        this.cyl = cyl;
    }

    public int getBit() {
        return bit;
    }

    public void setBit(int bit) {
        this.bit = bit;
    }

    public int getSd() {
        return sd;
    }

    public void setSd(int sd) {
        this.sd = sd;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getPath() {
        return Path;
    }

    public void setPath(String path) {
        Path = path;
    }

    public String getTime() {
        return Time;
    }

    public void setTime(String time) {
        Time = time;
    }

    public String getFileSize() {
        return FileSize;
    }

    public void setFileSize(String fileSize) {
        FileSize = fileSize;
    }

    public boolean isZhuanhuan() {
        return isZhuanhuan;
    }

    public void setZhuanhuan(boolean zhuanhuan) {
        isZhuanhuan = zhuanhuan;
    }

    public List<PcmBean> getPcmBeans() {
        return pcmBeans;
    }

    public void setPcmBeans(List<PcmBean> pcmBeans) {
        this.pcmBeans = pcmBeans;
    }

    public boolean isZhankai() {
        return isZhankai;
    }

    public void setZhankai(boolean zhankai) {
        isZhankai = zhankai;
    }
}

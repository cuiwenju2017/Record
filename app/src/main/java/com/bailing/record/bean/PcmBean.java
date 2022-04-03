package com.bailing.record.bean;

import org.litepal.crud.LitePalSupport;

import java.io.File;

public class PcmBean extends LitePalSupport {

    private long id;
    private int cyl;//采样率
    private int bit; //bit位
    private int sd;//声道
    private File file;//文件
    private String Name;//文件名
    private String Path;//文件路径
    private String Time;//文件日期
    private String FileSize;//文件大小
    private PcmLongBean pcmLongBean;
    private Boolean isZhuanhuan;

    public Boolean getZhuanhuan() {
        return isZhuanhuan;
    }

    public void setZhuanhuan(Boolean zhuanhuan) {
        isZhuanhuan = zhuanhuan;
    }

    public PcmLongBean getPcmLongBean() {
        return pcmLongBean;
    }

    public void setPcmLongBean(PcmLongBean pcmLongBean) {
        this.pcmLongBean = pcmLongBean;
    }

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
}

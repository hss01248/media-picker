package com.hss01248.media_picker.internal;

import java.io.Serializable;

/**
 * Photo Picker 运行时配置。
 */
public class PhotoPickerConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public int maxSelectNum = 1;
    public int maxVideoSelectNum = 1;
    /** 单位 MB，0 不限制 */
    public float maxFileSizeMb = 0f;
    /** 单位秒，0 不限制 */
    public int videoMaxSecond = 0;
    public boolean withVideo = true;
    public boolean withImage = true;
    /**
     * true：拷贝到外部私有目录后返回绝对路径；
     * false（默认）：直接返回 Uri.toString()。
     */
    public boolean copyToPath = false;
}

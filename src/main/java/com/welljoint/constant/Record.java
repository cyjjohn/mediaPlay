package com.welljoint.constant;

public interface Record {
    String ID = "CSID"; //录音流水号
    String SORT_ID = "SortID"; //录音拼接顺序
    String PATH_NAME = "PathName"; //存储盘路径
    String FILE_NAME = "FileName"; //文件路径 例:xx/yy/zz/aa.nmf
    String REC_START_TIME = "RecordingStartTime"; //录音开始时间 yyyy-MM-dd HH:mm:ss.SSS
    String REC_END_TIME = "RecordingEndTime"; //录音结束时间 yyyy-MM-dd HH:mm:ss.SSS
    String CUT_OFFSET = "CutOffset"; //录音拼接顺序
    String CUT_DURATION = "CutDuration"; //录音拼接顺序
}
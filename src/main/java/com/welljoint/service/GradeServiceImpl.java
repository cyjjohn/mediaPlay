package com.welljoint.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.welljoint.CommonUtil;
import com.welljoint.constant.AudioParam;
import com.welljoint.constant.Record;
import com.welljoint.util.TranscodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GradeServiceImpl implements GradeService {
    @Value("${prefix.origin}")
    private String originPath;

    @Value("${prefix.audio}")
    private String audioPath;

    @Value("${proc.name}")
    private String procName;

    @Override
    public void count(String jsonStr) {
        JSONObject jsonObject = JSONUtil.parseObj(jsonStr);
        Long userId = jsonObject.getLong("UserID");
        if (userId == null) {
            return;
        }
        String interactionId = jsonObject.getStr("InteractionID");
        if (StrUtil.isBlank(interactionId)) {
            log.info("录音:{} 调用spCoachSetCount计数", interactionId);
            Connection conn = null;
            try {
                conn = Db.use().getConnection();
                String[] params = {jsonStr};
                Map<String, Integer> map = new LinkedHashMap<>();
                map.put("out_Status", Types.INTEGER);
                map.put("out_Content", Types.VARCHAR);
                List<Object> result = CommonUtil.getResult(conn, "spCoachSetCount", params, map);
                int lastIndex = result.size() - 1;
                log.info(String.valueOf(result.get(lastIndex)));
            } catch (SQLException e) {
                log.info(e.getMessage());
            } finally {
                try {
                    if (conn != null) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param json
     * @return
     */
    public String makeFile(JSONObject json) {
        String interactionId = json.getStr(AudioParam.INTERACTION_ID);
        if (interactionId == null) {
            json.remove(AudioParam.INTERACTION_ID);
        }
        Long userId = json.getLong("UserID");
        if (userId == null) {
            userId = 1L;
            json.set(AudioParam.USER_ID, userId);
        }
        Integer sId = json.getInt(AudioParam.SID);
        if (sId == null) {
            sId = 1;
            json.set(AudioParam.SID, sId);
        }
        String contactId = json.getStr(AudioParam.CONTACT_ID);
        String format = json.getStr(AudioParam.FORMAT);

        //判断流水号是否存在
        String audio = "";
        String id = "";
        if (StrUtil.isNotBlank(contactId)) {
            if (StrUtil.isNotBlank(interactionId)) {
                audio = CommonUtil.endsWithBar(audioPath) + interactionId + format;
                id = interactionId;
            } else {
                audio = CommonUtil.endsWithBar(audioPath) + contactId + format;
                id = contactId;
            }
        } else {
            log.error("流水号不存在");
        }
        boolean exist = FileUtil.exist(audio);
        if (exist) {
            return id + format;
        }

        Connection conn = null;
        try {
            //查询数据库 得到待转码录音列表
            conn = Db.use().getConnection();
            String[] params = {JSONUtil.toJsonStr(json)};
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("out_Status", Types.INTEGER);
            map.put("out_Content", Types.VARCHAR);
            List<Object> result = CommonUtil.getResult(conn, procName, params, map);
            int lastIndex = result.size() - 1; //查询结果集固定在最后，前面为出参
            if (result.size() == 3) {
                List<LinkedHashMap<String, String>> recordList = (List<LinkedHashMap<String, String>>) result.get(lastIndex);
                log.info(String.valueOf(result.get(lastIndex)));

                List<String> pathList = new ArrayList<>(); //待拼接列表
                String finalDestPath = CommonUtil.endsWithBar(audioPath) + id + format; //最终输出文件完整路径
                if (recordList == null || recordList.size() == 0) {
                    log.info("存储过程返回为空,没有待转码录音");
                    return "";
                }
                for (LinkedHashMap<String, String> record : recordList) {
                    log.info("录音ID:{} 待转码分段录音总数:" + recordList.size(), id);
                    String pathName = CommonUtil.startsWithBar(record.get(Record.PATH_NAME));
                    int count = 0; //计数
                    count++;
                    String fileName = record.get(Record.FILE_NAME);
                    String suffix = fileName.substring(fileName.lastIndexOf("."));
                    String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                    String originFullPath = CommonUtil.endsWithBar(originPath) + id + "_" + count + suffix;
                    File srcFile = new File(srcPath);
                    FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                    log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                    String partPath = CommonUtil.endsWithBar(audioPath) + id + "_" + count + format;
                    Integer offset = Integer.parseInt(String.valueOf(record.get(Record.CUT_OFFSET)));
                    Integer duration = Integer.parseInt(String.valueOf(record.get(Record.CUT_DURATION)));
                    String destPath = TranscodeUtil.execTranscodeCmdStr(srcPath, partPath, offset, duration, suffix, format);
                    if (StrUtil.isNotBlank(destPath)) {
                        pathList.add(destPath);
                    } else {
                        log.info("转码发送错误:{}", srcPath);
                    }
                }
                //拼接
                boolean flag = TranscodeUtil.execCombineRecord(pathList, finalDestPath);
                if (flag) {
                    return id + format;
                } else {
                    log.info("拼接失败:{}", id);
                }
            } else {
                log.info(result.get(1).toString());
            }
        } catch (SQLException e) {
            log.info(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
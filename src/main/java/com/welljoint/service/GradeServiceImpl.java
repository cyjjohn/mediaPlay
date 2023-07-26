package com.welljoint.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.welljoint.CommonUtil;
import com.welljoint.constant.Record;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GradeServiceImpl implements GradeService {

    @Value("${suffix.file}")
    private String suffix;
    
    @Value("${prefix.origin}")
    private String originPath;

    @Value("${prefix.wav}")
    private String wavPath;

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
            log.info("调用spCoachSetCount计数");
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

    /***
     * @description
     * @param jsonStr
     * @return java.lang.String
     * @author cyjjohn
     * @date 2022/7/14 15:12
     */
    public String makeFile(String jsonStr) {
        JSONObject jsonObject = JSONUtil.parseObj(jsonStr);
        String interactionId = jsonObject.getStr("InteractionID");
        if (interactionId==null) {
            jsonObject.remove("InteractionID");
        }
        String contactId = jsonObject.getStr("ContactID");
        String format = jsonObject.getStr("Format");
        if (StrUtil.isBlank(format)) {
            format = ".mp3";
        }
        Long userId = jsonObject.getLong("UserID");
        if (userId == null) {
            userId = 1L;
            jsonObject.set("UserID",userId);
        }
        Integer sId = jsonObject.getInt("SID");
        if (sId == null) {
            sId = 1;
            jsonObject.set("SID", sId);
        }

        //判断流水号是否存在
        String wav = "";
        String id = "";
        if (StrUtil.isNotBlank(contactId)) {
            if (StrUtil.isNotBlank(interactionId)) {
                wav = CommonUtil.endsWithBar(wavPath) + interactionId + format;
                id = interactionId;
            } else {
                wav = CommonUtil.endsWithBar(wavPath) + contactId + format;
                id = contactId;
            }
        } else {
            log.error("流水号不存在");
        }
        boolean exist = FileUtil.exist(wav);
        if (exist) {
            return id + format;
        }

        Connection conn = null;
        try {
            //查询数据库 得到待转码录音列表
            conn = Db.use().getConnection();
            String[] params = {JSONUtil.toJsonStr(jsonObject)};
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("out_Status", Types.INTEGER);
            map.put("out_Content", Types.VARCHAR);
            List<Object> result = CommonUtil.getResult(conn, procName, params, map);
            int lastIndex = result.size() - 1; //查询结果集固定在最后，前面为出参
            if (result.size() == 3) {
                List<LinkedHashMap<String, String>> recordList = (List<LinkedHashMap<String, String>>) result.get(lastIndex);
                log.info(String.valueOf(result.get(lastIndex)));
                if (null != recordList && recordList.size() > 0) {
                    log.info("待转码分段录音总数:" + recordList.size());
                    LinkedHashMap<String, String> record = recordList.get(0);
                    String pathName = CommonUtil.startsWithBar(record.get(Record.PATH_NAME));
                    String wavFullPath = CommonUtil.endsWithBar(wavPath) + id + format; //最终输出文件完整路径

                    if (recordList.size() == 1) {
                        log.info("录音ID:" + id + "共1段,无需拼接");
                        //拷贝挂载盘录音到本地
                        String fileName = record.get(Record.FILE_NAME);
                        String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                        String originFullPath = CommonUtil.endsWithBar(originPath) + id + suffix;
                        File srcFile = new File(srcPath);
                        FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                        log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                        //转码
                        String offset = String.valueOf(record.get(Record.CUT_OFFSET));
                        String duration = String.valueOf(record.get(Record.CUT_DURATION));
                        Boolean flag = execTranscodeCmd(originFullPath, wavFullPath, offset, duration, format);
                        if (flag) {
                            log.info("转码成功,录音:" + id + ",保存至" + wavFullPath);
                        }else{
                            return "转码失败,录音:" + id;
                        }
                        FileUtil.del(originFullPath); //清理保存到本地的原始文件
                    } else {
                        log.info("录音ID:" + id + "共" + recordList.size() + "段,需拼接");
                        int count = 0; //计数
                        List<String> wavNameList = new ArrayList<>();

                        for (LinkedHashMap<String, String> part : recordList) { //循环内为需拼接的一通完整录音
                            count++;
                            String fileName = part.get(Record.FILE_NAME);
                            String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                            String originFullPath = CommonUtil.endsWithBar(originPath) + id + "_" + count  + suffix;
                            File srcFile = new File(srcPath);
                            FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                            log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                            String outPath = CommonUtil.endsWithBar(wavPath) + id + "_" + count + format;
                            String offset = String.valueOf(part.get(Record.CUT_OFFSET));
                            String duration = String.valueOf(part.get(Record.CUT_DURATION));
                            boolean flag = execTranscodeCmd(originFullPath, outPath, offset, duration, format);
                            if (flag) {
                                wavNameList.add(outPath);
                                log.info("转码成功待拼接,录音:" + outPath);
                            }else{
                                return "转码失败,录音:" + id;
                            }
                            FileUtil.del(originFullPath); //清理保存到本地的原始文件
                        }

                        //拼接命令
                        StringBuilder cmd = new StringBuilder("tool/CombineWave " + wavFullPath);
                        for (String wavName : wavNameList) {
                            cmd.append(" ").append(wavName);
                        }
                        log.info(cmd.toString());
                        String res = RuntimeUtil.execForStr(cmd.toString());
                        if (StrUtil.isBlank(res)) {
                            for (String wavName : wavNameList) {
                                FileUtil.del(wavName);
                            }
                            log.info("拼接成功,录音ID:" + id + ",输出至" + wavFullPath);
                        }else{
                            log.info(res);
                            return "已转码,但拼接失败,录音ID:" + id;
                        }

                    }
                    return id + format;
                } else {
                    log.info("存储过程返回为空,没有待转码录音");
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

    /**
     * @description 执行转码命令
     * @param srcPath 源文件完整路径
     * @param destPath 目标路径
     * @param offset 时间偏移量
     * @param duration 持续时长
     * @param format 录音格式 例如.mp3或.wav
     * @return java.lang.String
     * @author cyjjohn
     * @date 2021/5/6 13:32
     */
    public Boolean execTranscodeCmd(String srcPath,String destPath, String offset, String duration, String format) {
        String result;

        String destFullPath = destPath.substring(0, destPath.lastIndexOf('.')) + format;
        String cmd = "";
        switch (format) {
            case ".wav":
                // ffmpeg -y -i in.aac -ar 8000 -ac 2 out.wav
                cmd = "tool/ffmpeg -y -i " + srcPath + " -ar 8000 -ac 2 " + destFullPath;
                break;
            case ".mp3":
            default:
                // ffmpeg -y -i in.aac -acodec libmp3lame -ar 8000 -ac 2 out.mp3
                cmd = "tool/ffmpeg -y -i " + srcPath + " -acodec libmp3lame -ar 8000 -ac 2 " + destFullPath;
                break;
        }
        log.info(cmd);
        result = RuntimeUtil.execForStr(cmd);
        log.info(result);

        //判断是否切割
        if (StrUtil.isNotBlank(offset) && StrUtil.isNotBlank(duration)) {
            int offsetInt = Integer.parseInt(offset);
            int durationInt = Integer.parseInt(duration);
            if (offsetInt >= 0 && durationInt > 0) {
                //读取wav时长
                cmd = "tool/ffmpeg -i " + destFullPath + " -hide_banner";
                result = RuntimeUtil.execForStr(cmd);
                String reg = "Duration:(.*?)\\.";
                Pattern p = Pattern.compile(reg);
                Matcher m = p.matcher(result);
                int maxSecond=0;
                if (m.find()) {
                    maxSecond = DateUtil.timeToSecond(m.group(1).trim());
                }
                if (offsetInt < maxSecond) {
                    String startTime = DateUtil.secondToTime(offsetInt);
                    String durationTime = DateUtil.secondToTime(durationInt);
                    //修改完整wav文件名,让输出的片段与原本流程的相同
                    log.info("录音:" + destFullPath + "开始切割");
                    String tmpName = destFullPath.substring(0, destFullPath.lastIndexOf(".")) + "_tmp" + format;
                    FileUtil.rename(FileUtil.file(destFullPath), tmpName, true);
                    //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                    cmd = "tool/ffmpeg -y -i " + tmpName + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + destFullPath;
                    result = RuntimeUtil.execForStr(cmd);
                    log.info(cmd);
                    log.info(result);

                    FileUtil.del(tmpName);
                }
            }
        }

        if (result.trim().endsWith("%")) { //成功时以%结尾
            return true;
        }else{
            log.info(result);
            return false;
        }
    }

}
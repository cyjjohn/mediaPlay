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

    /***
     * @description
     * @param jsonStr
     * @return java.lang.String
     * @author cyjjohn
     * @date 2022/7/14 15:12
     */
    public String makeWavFile(String jsonStr) {
        JSONObject jsonObject = JSONUtil.parseObj(jsonStr);
        String interactionId = jsonObject.getStr("InteractionID");
        String contactId = jsonObject.getStr("ContactID");

        //判断流水号是否存在
        String wav = "";
        String id = "";
        if (StrUtil.isNotBlank(contactId)) {
            if (StrUtil.isNotBlank(interactionId)) {
                wav = CommonUtil.endsWithBar(wavPath) + interactionId + Record.MP3_FORMAT;
                id = interactionId;
            } else {
                wav = CommonUtil.endsWithBar(wavPath) + contactId + Record.MP3_FORMAT;
                id = contactId;
            }
        } else {
            log.error("流水号不存在");
        }
        boolean exist = FileUtil.exist(wav);
        if (exist) {
            return id + Record.MP3_FORMAT;
        }

        Connection conn = null;
        try {
            //查询数据库 得到待转码录音列表
            conn = Db.use().getConnection();
            String[] params = {jsonStr};
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("out_Status", Types.INTEGER);
            map.put("out_Content", Types.VARCHAR);
            List<Object> result = CommonUtil.getResult(conn, procName, params, map);
            int lastIndex = result.size() - 1; //查询结果集固定在最后，前面为出参
            List<LinkedHashMap<String, String>> recordList = (List<LinkedHashMap<String, String>>) result.get(lastIndex);
            log.info(String.valueOf(result.get(lastIndex)));
            if (null != recordList && recordList.size() > 0) {
                log.info("待转码分段录音总数:" + recordList.size());
                LinkedHashMap<String, String> record = recordList.get(0);
                String pathName = CommonUtil.startsWithBar(record.get(Record.PATH_NAME));
                String wavFullPath = CommonUtil.endsWithBar(wavPath) + id + Record.MP3_FORMAT; //最终输出文件完整路径

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
                    Boolean flag = execTranscodeCmd(originFullPath, wavFullPath, offset, duration, Record.MP3_FORMAT);
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

                    //先循环遍历时间,看是否存在某条记录同时覆盖最小开始时间和最大结束时间
                    int index = getCoverIdx(recordList);
                    if (index != -1) {
                        log.info("录音:" + id + "为特殊情况,取最长段代替拼接");
                        LinkedHashMap<String, String> recordN = recordList.get(index);
                        String fileName = recordN.get(Record.FILE_NAME);
                        String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                        String originFullPath = CommonUtil.endsWithBar(originPath) + id + suffix;
                        File srcFile = new File(srcPath);
                        FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                        log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                        //转码
                        String offset = String.valueOf(recordN.get(Record.CUT_OFFSET));
                        String duration = String.valueOf(recordN.get(Record.CUT_DURATION));
                        Boolean flag = execTranscodeCmd(originFullPath, wavFullPath, offset, duration, Record.MP3_FORMAT);
                        if (flag) {
                            log.info("转码成功,录音输出至" + wavFullPath);
                        }else{
                            return "转码失败,录音:" + fileName;
                        }
                        FileUtil.del(originFullPath); //清理保存到本地的原始文件
                        return id + Record.MP3_FORMAT;
                    } else {
                        for (LinkedHashMap<String, String> part : recordList) { //循环内为需拼接的一通完整录音
                            count++;
                            String fileName = part.get(Record.FILE_NAME);
                            String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                            String originFullPath = CommonUtil.endsWithBar(originPath) + id + "_" + count  + suffix;
                            File srcFile = new File(srcPath);
                            FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                            log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                            String outPath = CommonUtil.endsWithBar(wavPath) + id + "_" + count + Record.MP3_FORMAT;
                            String offset = String.valueOf(part.get(Record.CUT_OFFSET));
                            String duration = String.valueOf(part.get(Record.CUT_DURATION));
                            boolean flag = execTranscodeCmd(originFullPath, outPath, offset, duration, Record.MP3_FORMAT);
                            if (flag) {
                                wavNameList.add(outPath);
                                log.info("转码成功待拼接,录音:" + outPath);
                            }else{
                                return "转码失败,录音:" + id;
                            }
                            FileUtil.del(originFullPath); //清理保存到本地的原始文件
                        }
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
                return id + Record.MP3_FORMAT;
            } else {
                log.info("存储过程返回为空,没有待转码录音");
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

        //ffmpeg -y -i in.aac -acodec libmp3lame -ar 8000 -ac 2 out.mp3
        String cmd = "tool/ffmpeg -y -i " + srcPath + " -acodec libmp3lame -ar 8000 -ac 2 " + destPath;
        log.info(cmd);
        result = RuntimeUtil.execForStr(cmd);
        log.info(result);

        //判断是否切割
        if (StrUtil.isNotBlank(offset) && StrUtil.isNotBlank(duration)) {
            int offsetInt = Integer.parseInt(offset);
            int durationInt = Integer.parseInt(duration);
            if (offsetInt >= 0 && durationInt > 0) {
                //读取wav时长
                cmd = "tool/ffmpeg -i " + destPath + " -hide_banner";
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
                    log.info("录音:" + destPath + "开始切割");
                    String originName = destPath;
                    String tmpName = destPath.substring(0, destPath.lastIndexOf(".")) + "_tmp" + format;
                    FileUtil.rename(FileUtil.file(originName), tmpName, true);
                    //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                    cmd = "tool/ffmpeg -y -i " + tmpName + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originName;
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

    /**
     * @description 获取单条覆盖全部时长的特殊录音的索引(下标从0开始),当不存在单条覆盖全部时返回-1
     * @param list 完整录音的片段集合
     * @return java.lang.Integer
     * @author cyjjohn
     * @date 2021/9/6 15:48
     */
    public Integer getCoverIdx(List<LinkedHashMap<String,String>> list) {
        List<Long> minList = new ArrayList<>();
        List<Long> maxList = new ArrayList<>();
        long min=Long.MAX_VALUE,max=0;
        for (Map<String, String> item : list) {
            //将开始时间和结束时间全部存储
            long minDate = DateUtil.parse(String.valueOf(item.get(Record.REC_START_TIME))).getTime();
            long maxDate = DateUtil.parse(String.valueOf(item.get(Record.REC_END_TIME))).getTime();
            minList.add(minDate);
            maxList.add(maxDate);
            if (minDate <= min) {
                min = minDate; //时间最小值
            }
            if (maxDate >= max) {
                max = maxDate; //时间最大值
            }
        }
        //最小、最大时间值的集合,两集合交集为最长段
        Set<Integer> minIndex=new HashSet<>(),maxIndex=new HashSet<>();
        for (int i = 0; i < minList.size(); i++) {
            if (min == minList.get(i)) {
                minIndex.add(i+1);
            }
            if (max == maxList.get(i)) {
                maxIndex.add(i+1);
            }
        }
        minIndex.retainAll(maxIndex);
        int index = 0;
        if (!minIndex.isEmpty()) {
            index = minIndex.iterator().next();
        }
        return index - 1;
    }
}
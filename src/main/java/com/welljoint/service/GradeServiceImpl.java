package com.welljoint.service;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import com.welljoint.CommonUtil;
import com.welljoint.constant.Record;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GradeServiceImpl implements GradeService {

    @Value("${prefix.nmf}")
    private String nmfPath;

    @Value("${prefix.wav}")
    private String wavPath;

    @Value("${proc.name}")
    private String procName;

    @Value("${proc.early.name}")
    private String procEarlyName;

    public String makeWavFile(String siteID, String interactionID, String extension, Date startTime) {
        String format = ".wav";
        String wav = CommonUtil.endsWithBar(wavPath) + interactionID + format;
        boolean exist = FileUtil.exist(wav);
        if (exist) {
            return interactionID + format;
        }

        Connection conn = null;
        try {
            //查询数据库 得到待转码录音列表
            long minute = DateUtil.between(startTime, new Date(), DateUnit.MINUTE);
            String db,proc;
            if (minute <= 60) {
                db = "db1";
                proc = procName;
            } else {
                db = "db2";
                proc = procEarlyName;
            }
            log.info("库:" + nmfPath);
            conn = Db.use(db).getConnection();
            String[] params = {siteID, interactionID, extension, DateUtil.format(startTime, "yyyy-MM-dd HH:mm:ss")};
            List<Object> result = CommonUtil.getResult(conn, proc, params);
            int lastIndex = result.size() - 1; //查询结果集固定在最后，前面为出参
            List<LinkedHashMap<String, String>> nmfList = (List<LinkedHashMap<String, String>>) result.get(lastIndex);
            log.info(String.valueOf(result.get(lastIndex)));
            if (null != nmfList && nmfList.size() > 0) {
                log.info("待转码分段录音总数:" + nmfList.size());
                LinkedHashMap<String, String> record = nmfList.get(0);
                String id = record.get(Record.ID);
                String pathName = record.get(Record.PATH_NAME);
                String wavFullPath = CommonUtil.endsWithBar(wavPath) + id + format; //最终输出文件完整路径

                if (nmfList.size() == 1) {
                    log.info("录音ID:" + id + "共1段,无需拼接");
                    //拷贝挂载盘录音到本地
                    String fileName = record.get(Record.FILE_NAME);
                    String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                    String nmfFullPath = CommonUtil.endsWithBar(nmfPath) + id + ".nmf";
                    File srcFile = new File(StrUtil.isNotBlank(srcPath) ? srcPath.replaceAll("\\\\", "/") : srcPath);
                    FileUtil.copy(srcFile, FileUtil.file(nmfFullPath), true);
                    log.info("录音" + id + "从" + srcPath + "拷贝至" + nmfFullPath);
                    //转码
                    String offset = String.valueOf(record.get(Record.CUT_OFFSET));
                    String duration = String.valueOf(record.get(Record.CUT_DURATION));
                    int flag = execTranscodeCmd(nmfFullPath, wavFullPath, offset, duration, format);
                    FileUtil.del(nmfFullPath); //清理nmf文件
                    if (flag==1) {
                        log.info("转码成功,录音:" + id + ",保存至" + wavFullPath);
                    }else if(flag==0){
                        log.info("转码放弃,录音:" + id);
                    }else if(flag==-1){
                        return "转码失败,录音:" + id;
                    }
                } else {
                    log.info("录音ID:" + id + "共" + nmfList.size() + "段,需拼接");
                    int count = 0; //计数
                    List<String> wavNameList = new ArrayList<>();

                    for (LinkedHashMap<String, String> part : nmfList) { //循环内为需拼接的一通完整录音
                        count++;
                        String fileName = part.get(Record.FILE_NAME);
                        String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                        String nmfFullPath = CommonUtil.endsWithBar(nmfPath) + id + "_" + count  + ".nmf";
                        File srcFile = new File(StrUtil.isNotBlank(srcPath) ? srcPath.replaceAll("\\\\", "/") : srcPath);
                        FileUtil.copy(srcFile, FileUtil.file(nmfFullPath), true);
                        log.info("录音" + id + "从" + srcPath + "拷贝至" + nmfFullPath);
                        String outPath = CommonUtil.endsWithBar(wavPath) + id + "_" + count + format;
                        String offset = String.valueOf(part.get(Record.CUT_OFFSET));
                        String duration = String.valueOf(part.get(Record.CUT_DURATION));
                        int flag = execTranscodeCmd(nmfFullPath, outPath, offset, duration, format);
                        FileUtil.del(nmfFullPath); //清理nmf文件
                        if (flag==1) {
                            wavNameList.add(outPath);
                            log.info("转码成功待拼接,录音:" + outPath);
                        }else if(flag==0){
                            log.info("转码放弃,录音:" + id + "第" + count + "段");
                        }else if(flag==-1){
                            return "转码失败,录音:" + id;
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
                return id + format;
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
     * @param format 后缀格式
     * @return java.lang.Integer -1失败 0放弃 1成功
     * @author cyjjohn
     * @date 2022-10-25 11:00:53
     */
    public Integer execTranscodeCmd(String srcPath,String destPath, String offset, String duration, String format) {
        String result;
        String fileName = srcPath.substring(0, srcPath.lastIndexOf("."))+"_"+System.currentTimeMillis();
        //.nmf 为nice录音文件  输出左右g729声道录音
        String cmd = "tool/nice/nmf2G729 " + srcPath + " " + fileName + "_C.g729 " + fileName + "_S.g729";
        result=RuntimeUtil.execForStr(cmd);
        log.info(cmd);
        if(StrUtil.isNotBlank(result)){ //成功时无信息
            log.info(result);
            return -1;
        }
        //左右声道g729转wav
        cmd = "tool/ffmpeg -y -acodec g729 -f g729 -i " + fileName + "_C.g729 " + fileName + "_C" + format;
        result = RuntimeUtil.execForStr(cmd);
        log.info(cmd);
        log.info(result);
        cmd = "tool/ffmpeg -y -acodec g729 -f g729 -i " + fileName + "_S.g729 " + fileName + "_S" + format;
        result = RuntimeUtil.execForStr(cmd);
        log.info(cmd);
        log.info(result);
        //判断是否切割
        if (StrUtil.isNotBlank(offset) && StrUtil.isNotBlank(duration)) {
            int offsetInt = Integer.parseInt(offset);
            int durationInt = Integer.parseInt(duration);
            //读取wav时长
            cmd = "tool/ffmpeg -i " + fileName + "_C" + format + " -hide_banner";
            result = RuntimeUtil.execForStr(cmd);
            String reg = "Duration:(.*?)\\.";
            Pattern p = Pattern.compile(reg);
            Matcher m = p.matcher(result);
            int maxSecond=0;
            if (m.find()) {
                maxSecond = DateUtil.timeToSecond(m.group(1).trim());
            }
            if (offsetInt >= 0 && offsetInt < maxSecond && durationInt > 0) {
                String startTime = DateUtil.secondToTime(offsetInt);
                String durationTime = DateUtil.secondToTime(durationInt);
                //修改完整wav文件名,让输出的片段wav与原本流程的相同
                log.info("录音:" + fileName + "开始切割");
                String originNameC = fileName + "_C" + format;
                String tmpNameC = fileName + "_C_TMP" + format;
                FileUtil.rename(FileUtil.file(originNameC), tmpNameC, true);
                String originNameS = fileName + "_S" + format;
                String tmpNameS = fileName + "_S_TMP" + format;
                FileUtil.rename(FileUtil.file(originNameS), tmpNameS, true);
                //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                cmd = "tool/ffmpeg -y -i " + tmpNameC + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameC;
                result = RuntimeUtil.execForStr(cmd);
                log.info(cmd);
                log.info(result);
                cmd = "tool/ffmpeg -y -i " + tmpNameS + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameS;
                result = RuntimeUtil.execForStr(cmd);
                log.info(cmd);
                log.info(result);
                FileUtil.del(tmpNameC);
                FileUtil.del(tmpNameS);
                //合并左右声道
                FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
                cmd = "tool/ffmpeg -y -i " + fileName + "_S" + format + " -i " + fileName + "_C" + format + " -ac 2 -filter_complex amix=inputs=2:duration=longest " + destPath;
                log.info(cmd);
                result = RuntimeUtil.execForStr(cmd);
                log.info(result);
                //清理文件
                FileUtil.del(fileName + "_C.g729");
                FileUtil.del(fileName + "_S.g729");
                FileUtil.del(fileName + "_C" + format);
                FileUtil.del(fileName + "_S" + format);
                if (result.trim().endsWith("%")) { //成功时以%结尾
                    return 1;
                }else{
                    log.info(result);
                    return -1;
                }
            } else if (offsetInt == 0 && durationInt == 0 && maxSecond > 0) {
                //合并左右声道
                FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
                cmd = "tool/ffmpeg -y -i " + fileName + "_S" + format + " -i " + fileName + "_C" + format + " -ac 2 -filter_complex amix=inputs=2:duration=longest " + destPath;
                log.info(cmd);
                result = RuntimeUtil.execForStr(cmd);
                log.info(result);
                //清理文件
                FileUtil.del(fileName + "_C.g729");
                FileUtil.del(fileName + "_S.g729");
                FileUtil.del(fileName + "_C" + format);
                FileUtil.del(fileName + "_S" + format);
                if (result.trim().endsWith("%")) { //成功时以%结尾
                    return 1;
                } else {
                    log.info(result);
                    return -1;
                }
            } else{
                return 0;
            }

        }
        return -1;
    }

}
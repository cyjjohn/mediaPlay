package com.welljoint.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import com.welljoint.CommonUtil;
import com.welljoint.bean.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GradeServiceImpl implements GradeService {

    @Value("${prefix.origin}")
    private String originPath;

    @Value("${prefix.wav}")
    private String wavPath;

    @Value("${proc.name}")
    private String procName;

    @Value("${proc.minutes}")
    private int minutes;

    public String makeFile(Payload payload) {
        String area = payload.getArea();
        String agentId = payload.getAgentId();
        String beginTime = payload.getBeginTime();

//        //查询本地是否有录音文件
//        boolean exist = FileUtil.exist(wav);
//        if (exist) {
//            return id + format;
//        }

        Map<String, Object> paramMap = MapUtil
                .builder("BeginTime", (Object) beginTime)
                .put("AgentID", agentId)
                .put("Minutes", minutes)
                .build();
        String sql = "select concat(AgentID,date_format(BeginTime,'%y%m%d%H%i%s')) as InteractionID\n" +
                "\t,left(FileName,instr(FileName,'\\\\')-1) as PathName\n" +
                "    ,replace(right(FileName,length(FileName)-instr(FileName,'\\\\')+1),'\\\\','/') as FileName\n" +
                "from trecordinfo6\n" +
                "where BeginTime between timestampadd(minute,-@Minutes,@BeginTime) \n" +
                "and timestampadd(minute,@Minutes,@BeginTime)\n" +
                "and AgentID=@AgentID\n" +
                "order by abs(timestampdiff(second,BeginTime,@BeginTime))\n" +
                "limit 1";
        try {
            List<Entity> result = Db.use().query(sql, paramMap);
            for (Entity entity : result) {
                String id= entity.getStr("InteractionID");
                String pathName= entity.getStr("PathName");
                String fileName= entity.getStr("FileName");

                //拷贝挂载盘录音到本地
                String srcPath = CommonUtil.endsWithBar(pathName) + fileName;
                String suffix = fileName.substring(fileName.lastIndexOf(".")).trim().toLowerCase();
                String originFullPath = CommonUtil.endsWithBar(originPath) + id + suffix;

                //根据原始文件后缀判断怎么转码
                if (suffix.equals(".v3")) {

                }else if(suffix.equals(".wav")){

                }else {
                    log.info("原始文件后缀:{},无法转码",suffix);
                    return null;
                }
                String format = ".mp3";
                String outFullPath = CommonUtil.endsWithBar(wavPath) + id + format; //最终输出文件完整路径

                File srcFile = new File(srcPath);
                FileUtil.copy(srcFile, FileUtil.file(originFullPath), true);
                log.info("录音" + id + "从" + srcPath + "拷贝至" + originFullPath);
                //转码
                Boolean flag = execTranscodeCmd(originFullPath, outFullPath, suffix, format);
                if (flag) {
                    log.info("转码成功,录音:" + id + ",保存至" + outFullPath);
                } else {
                    return "转码失败,录音:" + id;
                }
                FileUtil.del(originFullPath); //清理保存到本地的原始文件
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info(e.toString());
        }

        return null;
    }

    /**
     * @param srcPath  源文件完整路径
     * @param destPath 目标路径
     * @param suffix   原始格式
     * @param format   转出录音格式 例如.mp3或.wav
     * @return java.lang.String
     * @description 执行转码命令
     * @author cyjjohn
     * @date 2021/5/6 13:32
     */
    public Boolean execTranscodeCmd(String srcPath, String destPath,String suffix, String format) {
        String result;

        String destFullPath = destPath.substring(0, destPath.lastIndexOf('.')) + format;
        String cmd = "";
        switch (suffix){
            case ".v3":
                cmd = "tool/ffmpeg -y -acodec adpcm_ima_oki -f s16le -ar 6000 -i ";
                break;
            case ".mp3":
            default:
                cmd = "tool/ffmpeg -y -i ";
                break;
        }
        switch (format) {
            case ".wav":
                // ffmpeg -y -i in.aac -ar 8000 -ac 2 out.wav
                cmd += srcPath + " -ar 8000 -ac 2 " + destFullPath;
                break;
            case ".mp3":
            default:
                // ffmpeg -y -i in.aac -acodec libmp3lame -ar 8000 -ac 2 out.mp3
                cmd += srcPath + " -acodec libmp3lame -ar 8000 -ac 2 " + destFullPath;
                break;
        }

        log.info(cmd);
        result = RuntimeUtil.execForStr(cmd);
        log.info(result);

        if (result.trim().endsWith("%")) { //成功时以%结尾
            return true;
        } else {
            log.info(result);
            return false;
        }
    }

}
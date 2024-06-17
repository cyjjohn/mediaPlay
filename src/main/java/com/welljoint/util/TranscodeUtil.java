package com.welljoint.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.welljoint.constant.AudioFormat;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class TranscodeUtil {

    public static Boolean execTranscodeCmd(String srcPath, String destPath, Integer offset, Integer duration, AudioFormat srcFormat, AudioFormat destFormat) {
        if (srcFormat == destFormat) {
            if(!srcPath.equals(destPath)) FileUtil.move(FileUtil.file(srcPath), FileUtil.file(destPath), true);
            log.info("无需转码,录音输出至:{}", destPath);
            return true;
        }
        String format = destFormat.getFileExtension();
        srcPath = srcPath.replaceAll("\\\\", "/");
        destPath = destPath.replaceAll("\\\\", "/");
        boolean flag = false;
        switch (srcFormat) {
            case AAC:
                flag = transcodeAac(srcPath, destPath, offset, duration, format);
                break;
            case V3:
                flag = transcodeV3(srcPath, destPath, format);
                break;
            case NMF:
                int result = transcodeNmf(srcPath, destPath, offset, duration, format);
                if (result > 0) flag = true;
                break;
            case MP3:
                flag = transcodeMp3(srcPath, destPath, format);
                break;
            default:
                break;
        }
        if (flag) log.info("转码成功,录音输出至:{}", destPath);
        return flag;
    }

    public static Boolean execTranscodeCmd(String srcPath, String destPath, Integer offset, Integer duration, String srcFormat, String destFormat) {
        AudioFormat srcFormatEnum = AudioFormat.fromString(srcFormat);
        AudioFormat destFormatEnum = AudioFormat.fromString(destFormat);
        return execTranscodeCmd(srcPath, destPath, offset, duration, srcFormatEnum, destFormatEnum);
    }

    public static String execTranscodeCmdStr(String srcPath, String destPath, Integer offset, Integer duration, String srcFormat, String destFormat) {
        AudioFormat srcFormatEnum = AudioFormat.fromString(srcFormat);
        AudioFormat destFormatEnum = AudioFormat.fromString(destFormat);
        boolean flag = execTranscodeCmd(srcPath, destPath, offset, duration, srcFormatEnum, destFormatEnum);
        if (flag) {
            return destPath.substring(0, destPath.lastIndexOf('.')) + destFormat;
        } else {
            return null;
        }
    }

    public static Boolean transcodeMp3(String srcPath, String destPath, String format) {
        FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
        String destFullPath = destPath.substring(0, destPath.lastIndexOf('.')) + format;
        //ffmpeg -y -i in.mp3 -acodec pcm_s16le -ar 8000 -ac 2 out.wav
        String cmd = "tool/ffmpeg -y -i " + srcPath + " -acodec pcm_s16le -ar 8000 -ac 2 " + destFullPath;
        return execThenGetRes(cmd);
    }

    public static Integer transcodeNmf(String srcPath, String destPath, Integer offset, Integer duration, String format) {
        boolean result;
        String fileName = srcPath.substring(0, srcPath.lastIndexOf("."));
        //.nmf 为nice录音文件 g711转wav
        String cmd = "python tool/convert.py " + srcPath;
        log.info(cmd);
        boolean flag = execThenGetRes(cmd);
        if (!flag) {
            return -1;
        }
        //判断左右声道g729是否为空并转wav
        int maxSecond = 0; //存储最大时长
        long cLength = FileUtil.file(fileName + "_C.wav").length();
        long sLength = FileUtil.file(fileName + "_S.wav").length();
        if (cLength == 0 && sLength == 0) {
            log.info(srcPath + " 不是有效录音");
        } else {
            String sign = cLength > 0 ? "C" : "S";
            //读取wav时长
            cmd = "tool/ffmpeg -i " + fileName + "_" + sign + ".wav" + " -hide_banner";
            String res = RuntimeUtil.execForStr(cmd);
            String reg = "Duration:(.*?)\\.";
            Pattern p = Pattern.compile(reg);
            Matcher m = p.matcher(res);
            if (m.find()) {
                maxSecond = DateUtil.timeToSecond(m.group(1).trim());
                log.info("{}读取到时长为{}", fileName, maxSecond);
            }
            sign = sign.equals("C") ? "S" : "C";
            long length = FileUtil.file(fileName + "_" + sign + ".wav").length();
            if (length == 0) {
                //ffmpeg -y -f lavfi -i anullsrc=r=8000:cl=mono -t 20 silence.mp3
                cmd = "tool/ffmpeg -y -f lavfi -i anullsrc=r=8000:cl=mono -t " + maxSecond + " " + fileName + "_" + sign + ".wav";
                execThenGetRes(cmd);
                log.info(fileName + "_" + sign + ".wav" + " 文件大小为空,生成空白音频");
            }
        }
        //判断是否切割
        if (offset != null && duration != null) {
            FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
            if (offset >= 0 && offset < maxSecond && duration > 0) {
                String startTime = DateUtil.secondToTime(offset);
                String durationTime = DateUtil.secondToTime(duration);
                //修改完整wav文件名,让输出的片段wav与原本流程的相同
                log.info("录音:" + fileName + "开始切割");
                String originNameC = fileName + "_C.wav";
                String tmpNameC = fileName + "_C_TMP.wav";
                FileUtil.rename(FileUtil.file(originNameC), tmpNameC, true);
                String originNameS = fileName + "_S.wav";
                String tmpNameS = fileName + "_S_TMP.wav";
                FileUtil.rename(FileUtil.file(originNameS), tmpNameS, true);
                if (!format.equals(".wav")) {
                    cmd = "tool/ffmpeg -y -i " + originNameC + " " + fileName + "_C" + format;
                    execThenGetRes(cmd);
                    FileUtil.del(originNameC);
                    originNameC = fileName + "_C" + format;
                    cmd = "tool/ffmpeg -y -i " + originNameS + " " + fileName + "_S" + format;
                    execThenGetRes(cmd);
                    FileUtil.del(originNameS);
                    originNameS = fileName + "_C" + format;
                }
                //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                cmd = "tool/ffmpeg -y -i " + tmpNameC + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameC;
                execThenGetRes(cmd);
                cmd = "tool/ffmpeg -y -i " + tmpNameS + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameS;
                execThenGetRes(cmd);
                FileUtil.del(tmpNameC);
                FileUtil.del(tmpNameS);
                //合并左右声道
                cmd = "tool/ffmpeg -y -i " + fileName + "_S" + format + " -i " + fileName + "_C" + format + " -filter_complex amovie=" + fileName + "_S" + format + "[l];amovie=" + fileName + "_C" + format + "[r];[l][r]amerge " + destPath;
                result = execThenGetRes(cmd);
                //清理文件
                FileUtil.del(fileName + "_C.wav");
                FileUtil.del(fileName + "_S.wav");
                return result ? 1 : -1;
            } else if (maxSecond > 0) {
                //合并左右声道
                cmd = "tool/ffmpeg -y -i " + fileName + "_S" + format + " -i " + fileName + "_C" + format + " -filter_complex amovie=" + fileName + "_S" + format + "[l];amovie=" + fileName + "_C" + format + "[r];[l][r]amerge " + destPath;
                result = execThenGetRes(cmd);
                //清理文件
                FileUtil.del(fileName + "_C.wav");
                FileUtil.del(fileName + "_S.wav");
                return result ? 1 : -1;
            } else {
                return 0;
            }
        }
        return -1;
    }

    public static Boolean transcodeV3(String srcPath, String destPath, String format) {
        FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
        String destFullPath = destPath.substring(0, destPath.lastIndexOf('.')) + format;
        String cmd = "tool/ffmpeg -y -i ";
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
        return execThenGetRes(cmd);
    }

    public static Boolean transcodeAac(String srcPath, String destPath, Integer offset, Integer duration, String format) {
        boolean result;

        String destFullPath = destPath.substring(0, destPath.lastIndexOf('.')) + format;
        String cmd;
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
        result = execThenGetRes(cmd);

        //判断是否切割
        if (offset != null && duration != null) {
            if (offset >= 0 && duration > 0) {
                //读取wav时长
                cmd = "tool/ffmpeg -i " + destFullPath + " -hide_banner";
                String res = RuntimeUtil.execForStr(cmd);
                String reg = "Duration:(.*?)\\.";
                Pattern p = Pattern.compile(reg);
                Matcher m = p.matcher(res);
                int maxSecond = 0;
                if (m.find()) {
                    maxSecond = DateUtil.timeToSecond(m.group(1).trim());
                }
                if (offset < maxSecond) {
                    String startTime = DateUtil.secondToTime(offset);
                    String durationTime = DateUtil.secondToTime(duration);
                    //修改完整wav文件名,让输出的片段与原本流程的相同
                    log.info("录音:" + destFullPath + "开始切割");
                    String tmpName = destFullPath.substring(0, destFullPath.lastIndexOf(".")) + "_tmp" + format;
                    FileUtil.rename(FileUtil.file(destFullPath), tmpName, true);
                    //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                    cmd = "tool/ffmpeg -y -i " + tmpName + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + destFullPath;
                    result = execThenGetRes(cmd);
                    FileUtil.del(tmpName);
                }
            }
        }
        return result;
    }

    /**
     * 拼接录音
     *
     * @param fileNameList 录音文件名列表
     * @param destPath     最终输出文件完整路径
     * @return 是否成功
     */
    public static Boolean execCombineRecord(List<String> fileNameList, String destPath) {
        boolean flag = false;
        List<String> filteredList = fileNameList.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
        if (filteredList.size() == 0) {
            log.info("无拼接内容");
        } else if (filteredList.size() == 1) {
            //CombineWave处理单条录音会损坏
            String srcPath = filteredList.get(0);
            if (!srcPath.equals(destPath)) {
                FileUtil.copy(srcPath, destPath, true);
            }
            log.info("录音:{}仅1条,不拼接", destPath);
            flag = true;
        } else {
            StringBuilder cmd;
            // ffmpeg -i input1.wav -i input2.wav -i input3.wav -filter_complex "[0:a][1:a][2:a]concat=n=3:v=0:a=1[out]" -map "[out]" output.wav
            cmd = new StringBuilder("tool/ffmpeg -y");
            for (int i = 0; i < filteredList.size(); i++) {
                String fileName = filteredList.get(i);
                if (fileName.equals(destPath)) {
                    // 原文件名与输出文件名相同时 修改原文件名为临时文件名
                    String tmpName = fileName.substring(0, fileName.lastIndexOf(".")) + "_tmp" + fileName.substring(fileName.lastIndexOf("."));
                    FileUtil.rename(FileUtil.file(fileName), tmpName, true);
                    cmd.append(" -i ").append(tmpName);
                    filteredList.set(i, tmpName);
                } else {
                    cmd.append(" -i ").append(fileName);
                }
            }
            cmd.append(" -filter_complex ");
            for (int i = 0; i < filteredList.size(); i++) {
                cmd.append("[").append(i).append(":a]");
            }
            cmd.append("concat=n=").append(fileNameList.size()).append(":v=0:a=1[out] -map [out] ").append(destPath);
            FileUtil.mkParentDirs(destPath);
            log.info(cmd.toString());
            flag = execThenGetRes(cmd.toString());
            if (flag) {
                log.info("拼接成功,录音输出至" + destPath);
            } else {
                log.info("已转码,但拼接失败,录音:{}", destPath);
            }
        }
        for (String fileName : fileNameList) {
            log.info("拼接前原录音文件清理:{}", fileName);
            FileUtil.del(fileName);
        }
        return flag;
    }

    public static String execSplitCmdStr(String srcPath, String destPath, Integer offset, Integer duration, String srcFormat, String destFormat) {
        AudioFormat srcFormatEnum = AudioFormat.fromString(srcFormat);
        AudioFormat destFormatEnum = AudioFormat.fromString(destFormat);
        boolean flag = execSplitCmd(srcPath, destPath, offset, duration, srcFormatEnum, destFormatEnum);
        if (flag) {
            return destPath.substring(0, destPath.lastIndexOf('.')) + destFormat;
        } else {
            return null;
        }
    }

    public static Boolean execSplitCmd(String srcPath, String destPath, Integer offset, Integer duration, AudioFormat srcFormat, AudioFormat destFormat) {
        String format = destFormat.getFileExtension();
        srcPath = srcPath.replaceAll("\\\\", "/");
        destPath = destPath.replaceAll("\\\\", "/");
        boolean flag = false;
        switch (srcFormat) {
            case WAV:
            case AAC:
            case MP3:
                flag = splitCommon(srcPath, destPath, format);
                break;
            case V3:
                boolean transcodeFlag = transcodeV3(srcPath, destPath, format);
                if (transcodeFlag) {
                    srcPath = destPath;
                    splitCommon(srcPath, destPath, format);
                }
                break;
            case NMF:
                int result = splitNmf(srcPath, destPath, offset, duration, format);
                if (result > 0) flag = true;
                break;
            default:
                break;
        }
        if (flag) log.info("分离成功,录音输出至:{}", destPath);
        return flag;
    }

    /**
     * 通用分离双声道
     *
     * @param srcPath
     * @param destPath
     * @param format
     * @return
     */
    public static Boolean splitCommon(String srcPath, String destPath, String format) {
        FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
        String fileName = destPath.substring(0, destPath.lastIndexOf('.'));
        String leftName = fileName + "_0" + format;
        String rightName = fileName + "_1" + format;
        String destFullPath = fileName + format;
        //ffmpeg -i input.mp3 -map_channel 0.0.0 left.mp3 -map_channel 0.0.1 right.mp3
        String cmd = "tool/ffmpeg -y -i " + srcPath + " -map_channel 0.0.0 " + leftName + " -map_channel 0.0.1 " + rightName;
        log.info(cmd);
        boolean flag = execThenGetRes(cmd);
        if (flag) {
            FileUtil.rename(FileUtil.file(leftName), destFullPath, true);
            log.info("重命名左声道文件,输出至:{}", destFullPath);
            if (FileUtil.del(rightName)) {
                log.info("删除右声道文件:{}", rightName);
            }
            return true;
        } else {
            return false;
        }
    }

    public static Integer splitNmf(String srcPath, String destPath, Integer offset, Integer duration, String format) {
        String fileName = srcPath.substring(0, srcPath.lastIndexOf("."));
        //.nmf 为nice录音文件  输出左右g729声道录音
        String cmd = "python tool/convert.py " + srcPath;
        log.info(cmd);
        boolean flag = execThenGetRes(cmd);
        if (!flag) {
            return -1;
        }
        //判断左右声道g729是否为空并转wav
        int maxSecond = 0; //存储最大时长
        long cLength = FileUtil.file(fileName + "_C.wav").length();
        long sLength = FileUtil.file(fileName + "_S.wav").length();
        if (cLength == 0 && sLength == 0) {
            log.info(srcPath + " 不是有效录音");
        } else {
            String sign = cLength > 0 ? "C" : "S";
            //读取wav时长
            cmd = "tool/ffmpeg -i " + fileName + "_" + sign + ".wav" + " -hide_banner";
            String res = RuntimeUtil.execForStr(cmd);
            String reg = "Duration:(.*?)\\.";
            Pattern p = Pattern.compile(reg);
            Matcher m = p.matcher(res);
            if (m.find()) {
                maxSecond = DateUtil.timeToSecond(m.group(1).trim());
                log.info("{}读取到时长为{}秒", fileName, maxSecond);
            }
            sign = sign.equals("C") ? "S" : "C";
            long length = FileUtil.file(fileName + "_" + sign + ".wav").length();
            if (length == 0) {
                //ffmpeg -y -f lavfi -i anullsrc=r=8000:cl=mono -t 20 silence.mp3
                cmd = "tool/ffmpeg -y -f lavfi -i anullsrc=r=8000:cl=mono -t " + maxSecond + " " + fileName + "_" + sign + format;
                execThenGetRes(cmd);
                log.info(fileName + "_" + sign + ".wav" + " 文件大小为空,生成空白音频");
            }
        }
        //判断是否切割
        String originNameC = fileName + "_C.wav";
        String originNameS = fileName + "_S.wav";
        if (offset != null && duration != null) {
            FileUtil.mkdir(destPath.substring(0, destPath.lastIndexOf("/")));
            if (offset >= 0 && offset < maxSecond && duration > 0) {
                String startTime = DateUtil.secondToTime(offset);
                String durationTime = DateUtil.secondToTime(duration);
                //修改完整wav文件名,让输出的片段wav与原本流程的相同
                log.info("录音:" + fileName + "开始切割");
                String tmpNameC = fileName + "_C_TMP.wav";
                FileUtil.rename(FileUtil.file(originNameC), tmpNameC, true);
                String tmpNameS = fileName + "_S_TMP.wav";
                FileUtil.rename(FileUtil.file(originNameS), tmpNameS, true);
                //ffmpeg.exe -y -i 1.mp3 -acodec copy -ss 00:01:04 -t 00:00:30 output.mp3
                cmd = "tool/ffmpeg -y -i " + tmpNameC + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameC;
                execThenGetRes(cmd);
                cmd = "tool/ffmpeg -y -i " + tmpNameS + " -acodec copy -ss " + startTime + " -t " + durationTime + " " + originNameS;
                execThenGetRes(cmd);
                FileUtil.del(tmpNameC);
                FileUtil.del(tmpNameS);
                if (!format.equals(".wav")) {
                    cmd = "tool/ffmpeg -y -i " + originNameC + " " + fileName + "_C" + format;
                    execThenGetRes(cmd);
                    FileUtil.del(originNameC);
                    originNameC = fileName + "_C" + format;
                }
                FileUtil.rename(FileUtil.file(originNameC), destPath, true);
                log.info("重命名左声道文件,输出至:{}", destPath);
                if (FileUtil.del(originNameS)) log.info("删除右声道文件:{}", originNameS);
                return 1;
            } else if (maxSecond > 0) {
                moveAndCleanUp(originNameC, destPath);
                return 1;
            } else {
                return 0;
            }
        } else {
            moveAndCleanUp(originNameC, destPath);
            return 1;
        }
    }

    private static void moveAndCleanUp(String srcPath, String destPath) {
        FileUtil.rename(FileUtil.file(srcPath), destPath, true);
        log.info("重命名左声道文件,输出至:{}", destPath);
        String fileName = srcPath.replaceAll("_[C,S].wav", "");
        //清理文件
        FileUtil.del(fileName + "_C.wav");
        FileUtil.del(fileName + "_S.wav");
    }

    public static boolean execThenGetRes(String cmd) {
        Process process = RuntimeUtil.exec(null, cmd.split(" "));
        int result;
        try {
            result = process.waitFor();
            if (result == 0) {
                log.info("命令执行成功:{}", cmd);
                return true;
            } else {
                log.info("命令执行失败，错误信息如下:");
                log.info(IoUtil.read(process.getErrorStream(), CharsetUtil.CHARSET_UTF_8));
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        return false;
    }
}

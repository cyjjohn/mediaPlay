package com.welljoint.controller;

import com.welljoint.CommonUtil;
import com.welljoint.MediaResourceHttpRequestHandler;
import com.welljoint.service.GradeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * @title: ApiController
 * @Author cyjjohn
 * @Date: 2021/9/2 10:25
 */
@Slf4j
@RestController
@RequestMapping("/")
public class ApiController {
    @Autowired
    private MediaResourceHttpRequestHandler mediaHandler;

    @Autowired
    private GradeService gradeService;

    @Value("${server.domain}")
    private String domain;

    @Value("${prefix.wav}")
    private String wavPath;

    @RequestMapping({"/playWav"})
    public Map<String, Object> playWav(HttpServletRequest request, HttpServletResponse response, @RequestParam("wavName") String wavName) {
        Map<String, Object> result = new HashMap<>();

        File wavFile = new File(CommonUtil.endsWithBar(wavPath) + wavName);
        if (!wavFile.exists()) {
            result.put("code", 0);
            result.put("msg", "路径：" + wavName + "的文件名不存在");
            return result;
        }
        String range = request.getHeader("Range");
        long startByte = 0L;
        long endByte = wavFile.length() - 1L;

        if (range != null && range.contains("bytes=") && range.contains("-")) {
            range = range.substring(range.lastIndexOf("=") + 1).trim();
            String[] ranges = range.split("-");

            try {
                if (ranges.length == 1) {
                    if (range.startsWith("-")) {
                        endByte = Long.parseLong(ranges[0]);

                    } else if (range.endsWith("-")) {
                        startByte = Long.parseLong(ranges[0]);
                    }

                } else if (ranges.length == 2) {
                    startByte = Long.parseLong(ranges[0]);
                    endByte = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                startByte = 0L;
                endByte = wavFile.length() - 1L;
            }
        }

        long contentLength = endByte - startByte + 1L;
        String fileName = wavFile.getName();
        String contentType = request.getServletContext().getMimeType(fileName);
        response.setHeader("Accept-Ranges", "bytes");
        response.setStatus(206);
        response.setContentType(contentType);
        response.setHeader("Content-Type", contentType);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + wavFile.length());
        response.setHeader("Access-Control-Allow-Origin","*");
        BufferedOutputStream outputStream;
        RandomAccessFile randomAccessFile = null;

        long transmitted = 0L;
        try {
            randomAccessFile = new RandomAccessFile(wavFile, "r");
            outputStream = new BufferedOutputStream(response.getOutputStream());
            byte[] buff = new byte[4096];
            int len = 0;
            randomAccessFile.seek(startByte);

            while (transmitted + len <= contentLength && (len = randomAccessFile.read(buff)) != -1) {
                outputStream.write(buff, 0, len);
                transmitted += len;
            }

            if (transmitted < contentLength) {
                len = randomAccessFile.read(buff, 0, (int)(contentLength - transmitted));
                outputStream.write(buff, 0, len);
                transmitted += len;
            }
            outputStream.flush();
            response.flushBuffer();
            randomAccessFile.close();
            log.info("下载完毕：" + startByte + "-" + endByte + "：" + transmitted);
        } catch (ClientAbortException e) {
            log.info("用户停止下载：" + startByte + "-" + endByte + "：" + transmitted);
        }
        catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        result.put("code", 1);
        result.put("msg", "播放成功");
        return result;
    }

    @PostMapping({"/wavUrl"})
    @ResponseBody
    public String wavUrl(@RequestBody String jsonStr) {
        log.info(jsonStr);
        gradeService.count(jsonStr);
        String voiceName = gradeService.makeFile(jsonStr);
        return domain + voiceName;
    }
}

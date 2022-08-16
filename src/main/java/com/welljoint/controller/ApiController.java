package com.welljoint.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.welljoint.CommonUtil;
import com.welljoint.MediaResourceHttpRequestHandler;
import com.welljoint.bean.Payload;
import com.welljoint.service.GradeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
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

    /*@GetMapping("/play")
    @ResponseBody
    public void play(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getParameter("path");
        if (StrUtil.isNotBlank(path)) {
            path =  + path;
            Path filePath = Paths.get(path);
            File file = new File(path);
            try {
                String mimeType = Files.probeContentType(filePath);
                if (StrUtil.isEmpty(mimeType)) {
                    response.setContentType(mimeType);
                }
                request.setAttribute(MediaResourceHttpRequestHandler.ATTR_FILE, file);
                mediaHandler.handleRequest(request, response);
            } catch (ServletException | IOException e) {
                e.printStackTrace();
            }
        }else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        }
    }*/

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

//    @RequestMapping({"/getPlayWav"})
//    @ResponseBody
//    public void getPlayWav(HttpServletRequest request, HttpServletResponse response, @RequestParam("SiteID") String SiteID, @RequestParam("InteractionID") String InteractionID, @RequestParam("Extension") String Extension, @RequestParam("StartTime") String StartTime) {
//        String voiceName = gradeService.makeWavFile(SiteID, InteractionID, Extension,DateUtil.parse(StartTime, "yyyy-MM-dd HH:mm:ss"));
//
//        try {
//            response.addHeader("Accept-Ranges", "bytes");
//            response.addHeader("Content-Type", "audio/mpeg;charset=UTF-8");
//            File file = FileUtil.file(CommonUtil.endsWithBar(wavPath) + voiceName);
//            int len_l = (int)file.length();
//            response.addHeader("Content-Length", len_l + "");
//            String range = request.getHeader("Range");
//            String[] rs = range.split("\\=");
//            range = rs[1].split("\\-")[0];
//            int start = Integer.parseInt(range);
//            response.addHeader("Content-Range", "bytes " + start + "-" + (len_l - 1) + "/" + len_l);
//            byte[] buf = new byte[2048];
//            FileInputStream fis = new FileInputStream(file);
//            ServletOutputStream servletOutputStream = response.getOutputStream();
//            len_l = fis.read(buf);
//            while (len_l != -1) {
//                servletOutputStream.write(buf, 0, len_l);
//                len_l = fis.read(buf);
//            }
//            servletOutputStream.flush();
//            servletOutputStream.close();
//            fis.close();
//        } catch (Exception e) {
//            log.error("getPlayWav报错：" + e);
//        }
//    }

//    @GetMapping({"/wavUrl"})
//    @ResponseBody
//    public String getWavUrl(@RequestParam("SiteID") String SiteID, @RequestParam("InteractionID") String InteractionID, @RequestParam("Extension") String Extension, @RequestParam("StartTime") String StartTime) {
//        String voiceName = gradeService.makeWavFile(SiteID, InteractionID, Extension, DateUtil.parse(StartTime, "yyyy-MM-dd HH:mm:ss"));
//        return domain + voiceName;
//    }

    @PostMapping({"/wavUrl"})
    @ResponseBody
    public String wavUrl(@RequestBody String jsonStr) {
        log.info(jsonStr);
        String voiceName = gradeService.makeFile(jsonStr);
        return domain + voiceName;
    }
}

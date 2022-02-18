package com.welljoint;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * @title: ClearRunner
 * @Author cyjjohn
 * @Date: 2021/9/27 11:02
 */
@Slf4j
@Component
public class ClearRunner extends TimerTask implements CommandLineRunner {
    @Value("${prefix.wav}")
    private String wavPath;

    @Value("${schedule.time}")
    private Integer hour;

    @Value("${clear.percent}")
    private Integer percent;

    @Override
    public void run(String... args) {
        // 定时器对象
        Timer timer = new Timer();
        timer.schedule(this, 1000, 1000L * 3600 * hour);
        log.info("----定时清理器启动----");
        log.info("----每" + hour + "小时执行一次----");
    }

    @Override
    public void run() {
        log.info("定时清理运行中:正在遍历删除文件夹" + wavPath + "下的录音文件");
        File dir = FileUtil.file(wavPath);
        long usableSpace = dir.getUsableSpace();
        long totalSpace = dir.getTotalSpace();
        double overSize = (totalSpace - usableSpace) - totalSpace * percent / 100.0;
        DecimalFormat df  = new DecimalFormat("###.00");
        if (overSize > 0) {
            log.info("当前容量超出" + percent + "%有" +df.format(overSize / 1024 / 1024) + "MB");
            List<File> fileList = FileUtil.loopFiles(wavPath, pathname -> pathname.getName().endsWith(".wav"))
                    .stream().sorted(Comparator.comparingLong(File::lastModified)).collect(Collectors.toList());
            double count = 0;
            for (File file : fileList) {
                count += file.length();
                boolean flag = FileUtil.del(file);
                if (flag) {
                    log.info("定时清理运行中:" + file.getName() + "删除成功");
                } else{
                    log.info("定时清理运行中:" + file.getName() + "删除失败");
                }
                if (count > overSize) {
                    break;
                }
            }
        }else{
            log.info("当前容量距"+percent+"%还有" + df.format((-overSize) / 1024 / 1024) + "MB可用,不执行清理");
        }
        log.info("定时清理运行结束");
    }
}

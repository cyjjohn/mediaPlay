package com.welljoint.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * @title: Payload
 * @Author cyjjohn
 * @Date: 2021/9/28 10:38
 */
@Getter
@Setter
public class Payload {
    private String siteID;
    private String interactionID;
    private String extension;
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Override
    public String toString() {
        return String.format("{\"siteID\":\"%s\",\"interactionID\":\"%s\",\"extension\":\"%s\",\"startTime\":\"%s\"}", siteID, interactionID, extension, startTime);
    }
}

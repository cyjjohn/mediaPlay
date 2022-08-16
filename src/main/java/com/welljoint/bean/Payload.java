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
    private Long userId;
    private Integer sId;
    private String interactionId;
    private String contactId;
    @JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    private String contactStartTime;
    private String format;
}

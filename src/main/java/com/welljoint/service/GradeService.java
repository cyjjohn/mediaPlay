package com.welljoint.service;

import cn.hutool.json.JSONObject;

import java.util.Date;

public interface GradeService {
//  String makeWavFile(Long param1, Integer param2, String paramString3, Date paramDate,String paraString4);

  // 计数
  void count(String jsonStr);

  String makeFile(JSONObject json);

}
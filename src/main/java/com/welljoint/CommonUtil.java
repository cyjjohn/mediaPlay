package com.welljoint;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @title: CommonUtil
 * @Author cyjjohn
 * @Date: 2021/9/7 11:08
 */
@Slf4j
public class CommonUtil {
    public static String endsWithBar(String str){
        if (str != null) {
            if (!str.endsWith("/")) {
                str += "/";
                return str;
            }
            return str;
        } else {
            return null;
        }
    }

    public static String startsWithBar(String str) {
        if (str != null) {
            if (!str.startsWith("/")) {
                str = "/" + str;
                return str;
            }
            return str;
        } else {
            return null;
        }
    }

    public static String startsWithoutBar(String str) {
        if (str != null) {
            if (str.startsWith("/")) {
                return str.substring(1);
            }
            return str;
        } else {
            return null;
        }
    }

    public static String delEnter(String str) {
        return str.replaceAll("[\r\n]", "");
    }

    public static String formatPath(String str) {
        return str.replaceAll("\\\\", "/");
    }

    public static List<Object> getResult(Connection conn,String name, String[] params){
        List<Object> result = new ArrayList<>();
        String sql;
        CallableStatement cstm = null;
        boolean bl;
        ResultSet rs = null;
        try {
            StringBuilder paramStr = new StringBuilder();
            if (params.length == 0) {
                paramStr = new StringBuilder();
            } else {
                if (params.length == 1) {
                    paramStr = new StringBuilder("'" + params[0] + "'");
                } else {
                    for (String param : params) {
                        if(param != null){
                            param = param.replaceAll("'", "''"); //参数中的单引号改为两个单引号
                            param = "'" + param + "'";
                        }
                        paramStr.append(param).append(",");
                    }
                    paramStr.deleteCharAt(paramStr.length()-1);
                }
            }
            sql = String.format("{call %s(%s)}", name, paramStr.toString());
            log.info(sql);
            cstm = conn.prepareCall(sql);
            bl = cstm.execute(); //输出参数不会返回到结果集中，getXX方法专门用于获取输出参数
            while (bl) {
                rs = cstm.getResultSet();
                List<Map<String, Object>> resultMapList = new ArrayList<>();
                while (rs.next()) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int columnCount = rsmd.getColumnCount();
                    Map<String, Object> resultMap = new LinkedHashMap<>();
                    for (int i = 0; i < columnCount; i++) {
                        resultMap.put(rsmd.getColumnName(i + 1), rs.getObject(i + 1) instanceof Long ? String.valueOf(rs.getObject(i + 1)) : rs.getObject(i + 1));
                    }
                    resultMapList.add(resultMap);
                }
                result.add(resultMapList);
                bl = cstm.getMoreResults();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (cstm != null) {
                    cstm.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /*
     * outParams 存放输出参数的信息 <参数名称,参数类型>
     * */
    public static List<Object> getResult(Connection conn, String name, String[] params, Map<String,Integer> outParams){
        List<Object> result = new ArrayList<>();
        String sql;
        CallableStatement cstm = null;
        boolean bl;
        ResultSet rs = null;
        try {
            StringBuilder paramStr = new StringBuilder();
            if (params.length == 0) {
                paramStr = new StringBuilder();
            } else {
                if (params.length == 1) {
                    paramStr = new StringBuilder("'" + params[0] + "'");
                } else {
                    for (String param : params) {
                        if(param != null){
                            param = param.replaceAll("'", "''"); //参数中的单引号改为两个单引号
                            param = "'" + param + "'";
                        }
                    }
                    paramStr.deleteCharAt(paramStr.length()-1);
                }
            }

            int size = outParams.entrySet().size();
            StringBuilder mark = new StringBuilder();
            for (int i = 0; i < size; i++) {
                mark.append(",?");
            }
            sql = String.format("{call %s(%s" + mark.toString() + ")}", name, paramStr.toString());
            log.info(sql);
            cstm = conn.prepareCall(sql);
            for (Map.Entry<String, Integer> entry : outParams.entrySet()) {
                cstm.registerOutParameter(entry.getKey(), entry.getValue()); //注册输出参数,CallableStatement特有的方法
            }
            bl = cstm.execute(); //输出参数不会返回到结果集中，getXX方法专门用于获取输出参数
            for (Map.Entry<String, Integer> entry : outParams.entrySet()) {
                switch (entry.getValue()) {
                    case Types.INTEGER:
                        result.add(cstm.getInt(entry.getKey()));
                        break;
                    case Types.BIGINT:
                        result.add(cstm.getLong(entry.getKey()));
                        break;
                    case Types.VARCHAR:
                        result.add(cstm.getString(entry.getKey()));
                        break;
                    case Types.DATE:
                        result.add(cstm.getDate(entry.getKey()));
                        break;
                }
            }
            while (bl) {
                rs = cstm.getResultSet();
                List<Map<String, Object>> resultMapList = new ArrayList<>();
                while (rs.next()) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int columnCount = rsmd.getColumnCount();
                    Map<String, Object> resultMap = new LinkedHashMap<>();
                    for (int i = 0; i < columnCount; i++) {
                        resultMap.put(rsmd.getColumnName(i + 1), rs.getObject(i + 1) instanceof Long ? String.valueOf(rs.getObject(i + 1)) : rs.getObject(i + 1));
                    }
                    resultMapList.add(resultMap);
                }
                result.add(resultMapList);
                bl = cstm.getMoreResults();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (cstm != null) {
                    cstm.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
